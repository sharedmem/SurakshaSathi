"""
URL Intelligence — Stages 2, 3, 4 of the Detection Pipeline
=============================================================
Problem: "Fake mobile apps circulated via SMS/WhatsApp → phishing and fraud."

Implements the 4-stage URL analysis methodology from claude.txt:

Stage 2 — URL Intelligence (always, any URL found in SMS)
  A. Lexical features: length, entropy, digit/hyphen count, brand words, APK words
  B. TLD reputation: .icu .xyz .top → high risk
  C. Brand impersonation: Levenshtein + Jaro-Winkler vs official brand list
  D. window.location / window.href hostname extraction (JS redirect awareness)
  E. Local malicious domain blocklist lookup

Stage 3 — Lightweight Website Analysis (suspicious URLs: Stage2 risk >= 0.5,
  OR the domain is a known URL shortener — shorteners always mask their real
  destination, so lexical risk alone is not a reliable gate for them)
  Step 1: HTTP HEAD preflight → Content-Type (apk = CRITICAL), redirect chain
  Step 2: Redirect chain analysis → final domain, final file type, hop count
  Step 3: Static HTML fetch → title, visible text, APK hrefs, phishing form fields
  Model: heuristic scoring (XGBoost-equivalent without extra model weight)

The final decision (risk_level / is_apk_download / is_phishing_site) is made
from Stage 2 + Stage 3 output ONLY. There is no Stage 4 — a prior dynamic
-sandbox placeholder was removed since it was never invoked in production
(gated behind a flag that always defaulted off) and added indirection with
no signal.

QR code URLs flow through the SAME pipeline as SMS URLs.

Usage:
    from src.url_intelligence import URLIntelligence
    intel = URLIntelligence()
    result = intel.analyze("https://sbi-verify-update.xyz/SBI_Secure.apk")
"""

import re, os, math, time, urllib.parse
from typing import Dict, Any, List

try:
    import requests
    _REQUESTS = True
except ImportError:
    _REQUESTS = False

try:
    from Levenshtein import distance as _lev_lib
    _LEV_LIB = True
except ImportError:
    _LEV_LIB = False

try:
    import jellyfish
    _JARO_LIB = True
except ImportError:
    _JARO_LIB = False


# ── Constants ─────────────────────────────────────────────────────────────────

_LOW_REP_TLDS = {
    ".icu",".top",".xyz",".cc",".tk",".ml",".ga",".cf",".gq",
    ".buzz",".club",".online",".site",".fun",".live",".store",
    ".space",".pw",".work",".click",".link",".monster",".rest",
    ".cam",".bar",".surf",".loan",".ly",".bid",".stream",".download",".win",".vip",".date",
}
_APK_URL_WORDS = {
    "apk","xapk","apks","install","download","app-update","appupdate",
    "mparivahan","rbl-protect","sbi-update","update-app","secure-app",
    "security-app","bank-app","application",
}
_BRAND_WORDS = {
    "sbi","hdfc","icici","axis","kotak","paytm","phonepe","googlepay",
    "gpay","bhim","aadhaar","uidai","irctc","jio","airtel","vodafone",
    "bsnl","flipkart","amazon","rbl","bajaj","federal","indusind",
    "pnb","bob","canara","parivahan","mparivahan","npci",
}
_OFFICIAL = {
    "sbi.co.in","onlinesbi.sbi","hdfcbank.com","icicibank.com","axisbank.com",
    "kotak.com","paytm.com","phonepe.com","google.com","gpay.app",
    "bhimupi.org.in","uidai.gov.in","incometax.gov.in","irctc.co.in",
    "jio.com","airtel.in","amazon.in","flipkart.com","rblbank.com",
    "parivahan.gov.in",
}
_APK_EXTS  = {".apk",".xapk",".apks",".apkm",".aab",".ipa",".exe",".msi",".bin",".dex"}
_APK_CTYPE = {"application/vnd.android.package-archive","application/octet-stream","application/zip"}
_PHISHING_FIELDS = {
    "otp","aadhar","aadhaar","pan","panno","pancard","password","passwd",
    "pin","upipin","mpin","cardno","cardnumber","cvv","expiry","account",
    "accountno","ifsc",
}
_STAGE3_THRESH = 0.50

# Load local blocklist if it exists
_BLOCKLIST: set = set()
_BL_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                        "data","blocklist","malicious_domains.txt")
if os.path.exists(_BL_PATH):
    with open(_BL_PATH) as _f:
        _BLOCKLIST = {l.strip().lower() for l in _f if l.strip()}

# Known URL shorteners always mask their real destination — lexical/TLD risk
# on the shortener domain itself tells us nothing about the payload, so these
# must force Stage 3 (live redirect-chain resolution) regardless of Stage 2
# risk score. Loaded from config.yaml (falls back to a built-in list).
_URL_SHORTENERS: set = {
    "bit.ly","tinyurl.com","t.co","goo.gl","is.gd","v.gd","ow.ly","rebrand.ly",
    "cutt.ly","shorturl.at","rb.gy","surl.li","tiny.cc","clck.ru","bl.ink",
    "qrco.de","qr.io",
}
_CFG_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                         "configs","config.yaml")
if os.path.exists(_CFG_PATH):
    try:
        import yaml as _yaml
        with open(_CFG_PATH, encoding="utf-8") as _f:
            _cfg = _yaml.safe_load(_f) or {}
        _shorteners_cfg = _cfg.get("url_shorteners")
        if _shorteners_cfg:
            _URL_SHORTENERS = {s.strip().lower() for s in _shorteners_cfg}
    except Exception:
        pass


# ── Utilities ─────────────────────────────────────────────────────────────────

def _entropy(s: str) -> float:
    if not s: return 0.0
    cnt = {}
    for c in s: cnt[c] = cnt.get(c,0)+1
    n = len(s)
    return -sum((v/n)*math.log2(v/n) for v in cnt.values())


def _levenshtein(a: str, b: str) -> int:
    if _LEV_LIB: return _lev_lib(a, b)
    if len(a)<len(b): return _levenshtein(b,a)
    if not b: return len(a)
    prev = range(len(b)+1)
    for i,ca in enumerate(a):
        curr=[i+1]
        for j,cb in enumerate(b):
            curr.append(min(prev[j+1]+1,curr[j]+1,prev[j]+(ca!=cb)))
        prev=curr
    return prev[-1]


def _jaro_winkler(a: str, b: str) -> float:
    if _JARO_LIB: return jellyfish.jaro_winkler_similarity(a,b)
    if a==b: return 1.0
    la,lb=len(a),len(b)
    if not la or not lb: return 0.0
    md=max(la,lb)//2-1
    ma=[False]*la; mb=[False]*lb; matches=0
    for i,ca in enumerate(a):
        for j in range(max(0,i-md),min(i+md+1,lb)):
            if not mb[j] and ca==b[j]:
                ma[i]=mb[j]=True; matches+=1; break
    if not matches: return 0.0
    t=sum(c1!=c2 for c1,c2 in zip((c for c,m in zip(a,ma) if m),(c for c,m in zip(b,mb) if m)))//2
    j=(matches/la+matches/lb+(matches-t)/matches)/3
    pref=sum(1 for x,y in zip(a[:4],b[:4]) if x==y)
    return j+pref*0.1*(1-j)


def _domain(url: str) -> str:
    try:
        parsed=urllib.parse.urlparse(url if "://" in url else f"http://{url}")
        return parsed.netloc.lower().replace("www.","").split(":")[0]
    except Exception:
        return url.lower().split("/")[0]


def _tld(domain: str) -> str:
    parts=domain.split(".")
    return f".{parts[-1]}" if parts else ""


def _href_hostname(href: str) -> str:
    try:
        return urllib.parse.urlparse(href if "://" in href else f"http://{href}").hostname or ""
    except Exception:
        return ""


# ── Stage 2 ───────────────────────────────────────────────────────────────────

class Stage2URLIntelligence:
    _FILE_EXT_RE = re.compile(r'\.(apk|xapk|apks|apkm|aab|ipa|exe|msi|bat|bin|dex)$', re.I)

    def analyze(self, url: str) -> Dict[str, Any]:
        dom   = _domain(url)
        tld   = _tld(dom)
        path  = urllib.parse.urlparse(url if "://" in url else f"http://{url}").path.lower()
        full  = (dom+path).lower()
        dbase = dom.split(".")[0]

        # A. Lexical
        apk_words   = [w for w in _APK_URL_WORDS if w in full]
        brand_words = [w for w in _BRAND_WORDS   if w in full]
        has_apk_ext = bool(self._FILE_EXT_RE.search(path))
        href_host   = _href_hostname(url)
        href_tld    = _tld(href_host)

        # B. TLD
        low_rep = tld in _LOW_REP_TLDS
        official= dom in _OFFICIAL

        # C. Brand impersonation
        impers = []
        for brand in _BRAND_WORDS:
            if len(brand)<=3: continue
            if dom in _OFFICIAL: continue
            lev = _levenshtein(brand, dbase)
            jw  = _jaro_winkler(brand, dbase)
            if (0<lev<=2) or (jw>0.88 and brand!=dbase):
                impers.append({"brand":brand,"domain":dom,"lev":lev,"jw":round(jw,3)})

        in_bl = dom in _BLOCKLIST or url.lower() in _BLOCKLIST
        is_shortener = dom in _URL_SHORTENERS

        # Risk score
        risk = 0.0
        if has_apk_ext:              risk += 0.90
        if in_bl:                    risk += 0.90
        if low_rep:                  risk += 0.35
        if href_tld in _LOW_REP_TLDS: risk += 0.20
        if impers:                   risk += 0.35*len(impers)
        if brand_words and not official: risk += 0.20
        if apk_words:                risk += 0.25
        if dom.count("-")>2:         risk += 0.15
        if _entropy(dom)>4.0:        risk += 0.10
        if sum(c.isdigit() for c in dom)>4: risk += 0.10
        # A shortener hides the real destination entirely — bump risk enough
        # to always warrant live Stage 3 resolution of the redirect chain.
        if is_shortener:              risk += 0.30
        risk = min(1.0, risk)

        level = ("CRITICAL" if risk>=0.85 or has_apk_ext or in_bl
                 else "HIGH" if risk>=_STAGE3_THRESH
                 else "MEDIUM" if risk>=0.25
                 else "LOW")

        return {
            "url":url,"domain":dom,"tld":tld,"href_hostname":href_host,
            "lexical":{"length":len(url),"entropy":round(_entropy(dom),3),
                       "digit_count":sum(c.isdigit() for c in dom),
                       "hyphen_count":dom.count("-"),"apk_words":apk_words,
                       "brand_words":brand_words},
            "is_official":official,"is_low_rep_tld":low_rep,
            "has_apk_extension":has_apk_ext,"is_shortener":is_shortener,
            "impersonation_hits":impers,"in_blocklist":in_bl,
            "stage2_risk_score":round(risk,3),"risk_level":level,
            "run_stage3":is_shortener or risk>=_STAGE3_THRESH,
        }


# ── Stage 3 ───────────────────────────────────────────────────────────────────

class Stage3WebsiteAnalysis:
    _TO  = 8
    _MAX = 512*1024
    _UA  = ("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")

    def analyze(self, url: str) -> Dict[str, Any]:
        if not _REQUESTS:
            return {"error":"requests not installed","stage3_risk_score":0.0}
        r: Dict[str, Any] = {
            "url":url,"head_content_type":None,"is_apk_content_type":False,
            "redirect_chain":[],"redirect_count":0,"final_url":url,
            "final_domain":_domain(url),"final_file_type":None,
            "html_title":None,"html_visible_text":[],"html_apk_hrefs":[],
            "html_form_fields":[],"html_has_phishing_form":False,
            "stage3_risk_score":0.0,
        }
        # Step 1: HEAD
        try:
            hr = requests.head(url,headers={"User-Agent":self._UA},
                               timeout=self._TO,allow_redirects=True,verify=False)
            ct = hr.headers.get("Content-Type","").lower()
            r["head_content_type"]=ct; r["final_url"]=hr.url
            r["final_domain"]=_domain(hr.url)
            r["redirect_count"]=len(hr.history)
            r["redirect_chain"]=[x.url for x in hr.history]+[hr.url]
            if any(a in ct for a in _APK_CTYPE): r["is_apk_content_type"]=True
            p=urllib.parse.urlparse(hr.url).path.lower()
            for ext in _APK_EXTS:
                if p.endswith(ext): r["final_file_type"]=ext; break
        except Exception as e: r["head_error"]=str(e)

        # Step 2 already captured via history

        # Step 3: Static HTML
        try:
            gr=requests.get(r["final_url"],headers={"User-Agent":self._UA},
                            timeout=self._TO,allow_redirects=True,verify=False,stream=True)
            raw=b""
            for chunk in gr.iter_content(4096):
                raw+=chunk
                if len(raw)>=self._MAX: break
            self._parse(raw.decode("utf-8","replace"), r)
        except Exception as e: r["html_error"]=str(e)

        # Score
        risk=0.0
        if r["is_apk_content_type"]:          risk+=0.90
        if r.get("final_file_type"):           risk+=0.90
        if r["html_has_phishing_form"]:        risk+=0.75
        if r["html_apk_hrefs"]:                risk+=0.70
        if r["redirect_count"]>3:              risk+=0.20
        r["stage3_risk_score"]=round(min(1.0,risk),3)
        return r

    def _parse(self, html: str, r: dict):
        m=re.search(r'<title[^>]*>(.*?)</title>',html,re.I|re.S)
        if m: r["html_title"]=re.sub(r'<[^>]+>','',m.group(1)).strip()
        txt=re.sub(r'<script[^>]*>.*?</script>','',html,flags=re.I|re.S)
        txt=re.sub(r'<style[^>]*>.*?</style>','',txt,flags=re.I|re.S)
        txt=re.sub(r'<[^>]+>',' ',txt)
        r["html_visible_text"]=[w for w in txt.split() if len(w)>2][:50]
        hrefs=re.findall(r'href=["\']([^"\']+)["\']',html,re.I)
        r["html_apk_hrefs"]=[h for h in hrefs if any(h.lower().endswith(e) for e in _APK_EXTS)]
        names=re.findall(r'<input[^>]+(?:name|id|placeholder)=["\']([^"\']+)["\']',html,re.I)
        labels=re.findall(r'<label[^>]*>(.*?)</label>',html,re.I|re.S)
        fields=[n.lower() for n in names]+[re.sub(r'<[^>]+>','',l).strip().lower() for l in labels]
        pf=[f for f in fields if any(x in f for x in _PHISHING_FIELDS)]
        r["html_form_fields"]=pf; r["html_has_phishing_form"]=bool(pf)


# ── URLIntelligence facade ────────────────────────────────────────────────────

class URLIntelligence:
    """
    Unified two-stage URL analysis. Stage 2 always runs; Stage 3 is conditional
    (Stage2 risk >= 0.5, or the domain is a known URL shortener). The final
    decision (risk_level / is_apk_download / is_phishing_site) is derived from
    Stage 2 + Stage 3 output only — no further ML/LLM stage is needed.
    QR-decoded URLs are treated identically to SMS URLs.
    """
    def __init__(self, run_stage3: bool=True):
        self._s2=Stage2URLIntelligence()
        self._s3=Stage3WebsiteAnalysis() if run_stage3 else None

    def analyze(self, url: str) -> Dict[str, Any]:
        url=url.strip()
        if not url: return {"error":"empty URL","risk_level":"LOW"}

        s2=self._s2.analyze(url)
        result: Dict[str,Any]={
            "url":url,"domain":s2["domain"],"risk_level":s2["risk_level"],
            "is_apk_download":s2["has_apk_extension"],"is_phishing_site":False,
            "final_url":url,"redirect_count":0,
            "impersonated_brands":[h["brand"] for h in s2.get("impersonation_hits",[])],
            "stage2":s2,
        }
        if self._s3 and s2.get("run_stage3"):
            s3=self._s3.analyze(url)
            result["stage3"]=s3
            result["final_url"]=s3.get("final_url",url)
            result["redirect_count"]=s3.get("redirect_count",0)
            result["is_apk_download"]=(result["is_apk_download"]
                or s3.get("is_apk_content_type",False)
                or bool(s3.get("final_file_type"))
                or bool(s3.get("html_apk_hrefs")))
            result["is_phishing_site"]=s3.get("html_has_phishing_form",False)
            s3r=s3.get("stage3_risk_score",0)
            if result["is_apk_download"] or s3r>=_STAGE3_THRESH:
                result["risk_level"]="CRITICAL" if (result["is_apk_download"] or s3r>=0.75) else "HIGH"
        return result

    def analyze_batch(self, urls: List[str], delay: float=0.3) -> List[Dict[str,Any]]:
        out=[]
        for i,u in enumerate(urls):
            out.append(self.analyze(u))
            if i<len(urls)-1: time.sleep(delay)
        return out


if __name__=="__main__":
    import argparse,json
    ap=argparse.ArgumentParser()
    ap.add_argument("url",nargs="?",default="https://sbi-update.icu/SBI_Secure.apk")
    ap.add_argument("--no-stage3",action="store_true")
    args=ap.parse_args()
    intel=URLIntelligence(run_stage3=not args.no_stage3)
    print(json.dumps(intel.analyze(args.url),indent=2,ensure_ascii=False))
