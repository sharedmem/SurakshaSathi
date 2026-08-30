"""
Web Testing Interface — SMS Spam Detector
==========================================
Problem: "Fake mobile apps circulated via SMS/WhatsApp → phishing and financial fraud."

Self-contained Flask app for live testing of the full 3-stage pipeline.
Prints the ACTUAL local IP so you can open it on any Android device on the same Wi-Fi.

Features:
  - Paste any multilingual SMS → live SPAM/HAM verdict
  - Optional QR decoded text field (simulate ML Kit QR decode)
  - Live LLM threat report panel (if LLM_BACKEND env var is set)
  - /health and /test endpoints for reproducibility checks
  - All HTML/CSS/JS is self-contained — no CDN, works offline on Android

Usage:
    python src/app.py --config configs/config.yaml --model final_model_YYYYMMDD --port 8000

    Set LLM_BACKEND=anthropic (or openai/gemini/ollama) + API key for LLM panel.

    Then open on Android:  http://<printed-ip>:8000
"""

import argparse, json, os, socket, sys
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from flask import Flask, jsonify, render_template_string, request

# ── HTML (fully self-contained) ───────────────────────────────────────────────

_HTML = r"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>SMS Spam Detector</title>
<style>
:root{--bg:#f0f4f8;--card:#fff;--bd:#e2e8f0;--pri:#4a90e2;--prid:#357abd;
--tx:#2d3748;--mt:#718096;--sp-bg:#fff5f5;--sp-bd:#fc8181;--sp-tx:#c53030;
--hm-bg:#f0fff4;--hm-bd:#68d391;--hm-tx:#276749;
--tg:#ebf8ff;--tgx:#2c5282;--rl:#fff3cd;--rlx:#7b341e;}
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
background:var(--bg);min-height:100vh;padding:16px;color:var(--tx)}
h1{font-size:1.3rem;font-weight:700;text-align:center;margin:16px 0 3px}
.sub{font-size:.78rem;color:var(--mt);text-align:center;margin-bottom:16px}
.card{background:var(--card);border-radius:14px;
box-shadow:0 2px 10px rgba(0,0,0,.08);padding:16px;
width:100%;max-width:520px;margin:0 auto}
textarea{width:100%;min-height:100px;border:1.5px solid var(--bd);
border-radius:9px;padding:10px;font-size:.93rem;resize:vertical;
outline:none;transition:border-color .2s;color:var(--tx);font-family:inherit}
textarea:focus{border-color:var(--pri)}
.row{margin-top:8px;display:flex;align-items:center;gap:7px}
.row label{font-size:.78rem;color:var(--mt);white-space:nowrap}
.row input{flex:1;border:1.5px solid var(--bd);border-radius:7px;
padding:6px 9px;font-size:.83rem;outline:none;font-family:inherit;
transition:border-color .2s}
.row input:focus{border-color:var(--pri)}
.btn{margin-top:12px;width:100%;padding:12px;background:var(--pri);
color:#fff;border:none;border-radius:9px;font-size:.97rem;font-weight:600;
cursor:pointer;transition:background .2s}
.btn:hover{background:var(--prid)}
.btn:disabled{background:#a0aec0;cursor:default}
#spin{display:none;text-align:center;margin-top:12px;color:var(--mt);font-size:.88rem}
.dot{animation:blink 1.4s infinite both}
.dot:nth-child(2){animation-delay:.2s}.dot:nth-child(3){animation-delay:.4s}
@keyframes blink{0%,80%,100%{opacity:0}40%{opacity:1}}
#result{margin-top:14px;display:none}
.badge{display:flex;align-items:center;justify-content:center;gap:7px;
padding:12px;border-radius:11px;font-size:1.2rem;font-weight:700}
.badge.spam{background:var(--sp-bg);color:var(--sp-tx);border:2px solid var(--sp-bd)}
.badge.ham{background:var(--hm-bg);color:var(--hm-tx);border:2px solid var(--hm-bd)}
.grid{margin-top:10px;display:grid;grid-template-columns:1fr 1fr;gap:8px}
.box{background:#f7fafc;border-radius:9px;padding:8px 11px}
.box .lbl{font-size:.68rem;color:var(--mt);text-transform:uppercase;letter-spacing:.05em}
.box .val{font-size:.93rem;font-weight:600;margin-top:2px}
.bar-wrap{margin-top:10px;background:#edf2f7;border-radius:7px;height:9px;overflow:hidden}
.bar-fill{height:100%;border-radius:7px;transition:width .5s ease}
.bar-fill.spam{background:var(--sp-bd)}.bar-fill.ham{background:var(--hm-bd)}
.sec{margin-top:10px}
.sec .lbl{font-size:.68rem;color:var(--mt);text-transform:uppercase;letter-spacing:.05em}
.tags{display:flex;flex-wrap:wrap;gap:5px;margin-top:5px}
.tag{border-radius:18px;padding:2px 8px;font-size:.71rem}
.tag.vec{background:var(--tg);color:var(--tgx)}
.tag.rule{background:var(--rl);color:var(--rlx)}
.tag.url{background:#faf5ff;color:#553c9a}
.llm{margin-top:12px;background:#fffbeb;border:1.5px solid #f6e05e;
border-radius:9px;padding:11px}
.llm .lbl{font-size:.68rem;color:#92400e;text-transform:uppercase;letter-spacing:.05em;margin-bottom:5px}
.llm-row{display:flex;gap:7px;margin-bottom:4px;font-size:.8rem;align-items:flex-start}
.llm-k{color:#92400e;font-weight:600;min-width:115px;flex-shrink:0}
.llm-v{color:#78350f;line-height:1.4}
.llm-wait{color:#92400e;font-size:.8rem;font-style:italic}
.err{color:var(--sp-tx);font-size:.83rem;margin-top:10px;padding:8px;
background:var(--sp-bg);border-radius:7px}
.samples{max-width:520px;margin:16px auto 0}
.stitle{font-size:.75rem;color:var(--mt);text-align:center;margin-bottom:6px}
.smp{display:block;width:100%;text-align:left;background:var(--card);
border:1.5px solid var(--bd);border-radius:9px;padding:8px 12px;
font-size:.75rem;color:#4a5568;cursor:pointer;margin-bottom:6px;
transition:border-color .2s;font-family:inherit;line-height:1.4}
.smp:hover{border-color:var(--pri)}
.sb{display:inline-block;font-size:.65rem;font-weight:700;
border-radius:5px;padding:1px 5px;margin-right:4px}
.sb.spam{background:#fff0f0;color:var(--sp-tx)}
.sb.ham{background:#f0fff4;color:var(--hm-tx)}
</style>
</head>
<body>
<h1>🔍 SMS Spam Detector</h1>
<p class="sub">Indic-BERT · Multilingual · Live · Fake App Detection</p>
<div class="card">
  <textarea id="msg" placeholder="Paste any SMS here (Hindi, Bengali, Tamil, English …)&#10;Detects: APK links · QR codes · callback/vishing · phishing domains"></textarea>
  <div class="row"><label>Sender (opt):</label>
    <input id="sender" placeholder="+91XXXXXXXXXX or VM-SBIBNK"/></div>
  <div class="row"><label>QR decoded text (opt):</label>
    <input id="qr" placeholder="e.g. upi://collect?pa=... or https://..."/></div>
  <div class="row"><label>Or upload QR image:</label>
    <input id="qrImg" type="file" accept="image/*"/></div>
  <div class="row"><label>Attachment (ignored, not QR):</label>
    <select id="mediaType">
      <option value="">None</option>
      <option value="image">Image</option>
      <option value="video">Video</option>
    </select>
  </div>
  <button class="btn" id="btn" onclick="analyse()">Analyse →</button>
  <div id="spin">Analysing<span class="dot">.</span><span class="dot">.</span><span class="dot">.</span></div>
  <div id="result"></div>
</div>
<div class="samples">
<p class="stitle">Try a sample ↓</p>
<button class="smp" onclick="load(this)"
  data-text="Namaste, aapka RBL Bank account block ho gaya. Install karo:\nrbl-protect-acc.icu/RBL_Protect.apk">
  <span class="sb spam">SPAM</span>Banking APK phishing (Hinglish)</button>
<button class="smp" onclick="load(this)"
  data-text="आपके वाहन पर यातायात उल्लंघन दर्ज। जुर्माना: रु 50000। [अग्रेषित] RTOCHALAN.apk">
  <span class="sb spam">SPAM</span>Fake e-challan APK (Hindi)</button>
<button class="smp" onclick="load(this)"
  data-text="আপনার SBI KYC মেয়াদ শেষ। আজই অ্যাপ ইনস্টল করুন নইলে অ্যাকাউন্ট বন্ধ।"
  data-qr="https://sbi-kyc-update.xyz/SBI_Secure.apk">
  <span class="sb spam">SPAM</span>SBI KYC APK via QR (Bengali)</button>
<button class="smp" onclick="load(this)"
  data-text="Your SBI account suspended. Call 1800-111-2222 now. Our officer will help you install the security app to restore access."
  data-sender="+447911123456">
  <span class="sb spam">SPAM</span>Callback + APK install scam</button>
<button class="smp" onclick="load(this)"
  data-text="Scan the QR code to claim your ₹5000 Flipkart cashback reward!"
  data-qr="upi://collect?pa=scam@upi&pn=FlipkartReward&am=5000&mode=00">
  <span class="sb spam">SPAM</span>QR UPI collect scam</button>
<button class="smp" onclick="load(this)"
  data-text="Your OTP for SBI NetBanking is 847291. Valid 10 mins. Do not share. -SBI"
  data-sender="VM-SBIBNK">
  <span class="sb ham">HAM</span>Legitimate OTP</button>
<button class="smp" onclick="load(this)"
  data-text="Flipkart: Your order #OD8847120 shipped. Delivery by 28 Jun. Track: fkrt.it/t8xB">
  <span class="sb ham">HAM</span>Legitimate delivery notification</button>
</div>
<script>
function load(btn){
  document.getElementById('msg').value    = btn.getAttribute('data-text')||'';
  document.getElementById('sender').value = btn.getAttribute('data-sender')||'';
  document.getElementById('qr').value     = btn.getAttribute('data-qr')||'';
}
function fileToBase64(file){
  return new Promise((resolve,reject)=>{
    const r=new FileReader();
    r.onload=()=>resolve(r.result);
    r.onerror=reject;
    r.readAsDataURL(file);
  });
}
async function decodeQrImageIfPresent(){
  const f=document.getElementById('qrImg').files[0];
  if(!f) return document.getElementById('qr').value.trim();
  const b64=await fileToBase64(f);
  const resp=await fetch('/decode_qr',{method:'POST',
    headers:{'Content-Type':'application/json'},body:JSON.stringify({image_base64:b64})});
  const r=await resp.json();
  if(r.error) throw new Error(`QR decode failed: ${r.error}`);
  if(!r.decoded||!r.decoded.length) throw new Error('No QR code found in image');
  document.getElementById('qr').value=r.decoded[0];
  return r.decoded[0];
}
async function analyse(){
  const text=document.getElementById('msg').value.trim();
  const sender=document.getElementById('sender').value.trim();
  if(!text){alert('Please enter a message.');return;}
  let qr='';
  try{ qr=(await decodeQrImageIfPresent())||''; }
  catch(e){
    document.getElementById('result').innerHTML=`<div class="err">Error: ${e.message}</div>`;
    document.getElementById('result').style.display='block';
    return;
  }
  const btn=document.getElementById('btn');
  btn.disabled=true;
  document.getElementById('spin').style.display='block';
  document.getElementById('result').style.display='none';
  document.getElementById('result').innerHTML='';
  try{
    const body={text};
    if(sender) body.sender=sender;
    if(qr) body.qr_decoded_text=qr;
    const mediaType=document.getElementById('mediaType').value;
    if(mediaType) body.media_type=mediaType;
    const resp=await fetch('/analyse',{method:'POST',
      headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
    if(!resp.ok) throw new Error(`HTTP ${resp.status}`);
    render(await resp.json());
  }catch(e){
    document.getElementById('result').innerHTML=`<div class="err">Error: ${e.message}</div>`;
    document.getElementById('result').style.display='block';
  }finally{
    btn.disabled=false;
    document.getElementById('spin').style.display='none';
  }
}
function render(d){
  const cls=d.is_spam?'spam':'ham';
  const icon=d.is_spam?'🚨':'✅';
  const lbl=d.is_spam?'SPAM':'HAM';
  const pct=(d.spam_probability*100).toFixed(1);
  const vecs=d.sideloading_vectors||[];
  const urls=d.extracted_urls||[];
  const brands=d.detected_brands||[];
  const qrType=d.qr_payload_type||'none';
  const cb=d.callback_signals||{};
  let tags='';
  vecs.forEach(v=>{tags+=`<span class="tag vec">${v}</span>`;});
  if(qrType&&qrType!='none') tags+=`<span class="tag rule">QR:${qrType}</span>`;
  if(cb.has_callback_fraud) tags+=`<span class="tag rule">callback_fraud</span>`;
  if(cb.has_callback_apk_prompt) tags+=`<span class="tag rule">callback_apk_install</span>`;
  brands.forEach(b=>{tags+=`<span class="tag rule">brand:${b}</span>`;});
  let utags='';
  urls.slice(0,4).forEach(u=>{utags+=`<span class="tag url">${u}</span>`;});
  let trig=d.triggered_by||'';
  if(d.hard_rule_reason) trig+=` — ${d.hard_rule_reason}`;
  document.getElementById('result').innerHTML=`
    <div class="badge ${cls}">${icon} ${lbl}</div>
    <div class="grid">
      <div class="box"><div class="lbl">Spam Probability</div><div class="val">${pct}%</div></div>
      <div class="box"><div class="lbl">Threshold</div><div class="val">${(d.threshold_used*100).toFixed(1)}%</div></div>
      <div class="box"><div class="lbl">Triggered By</div><div class="val" style="font-size:.77rem">${trig}</div></div>
      <div class="box"><div class="lbl">Rule Confidence</div><div class="val">${(d.rule_confidence*100).toFixed(0)}%</div></div>
    </div>
    <div class="bar-wrap"><div class="bar-fill ${cls}" style="width:${pct}%"></div></div>
    ${tags?`<div class="sec"><div class="lbl">Detected Signals</div><div class="tags">${tags}</div></div>`:''}
    ${utags?`<div class="sec"><div class="lbl">Extracted URLs</div><div class="tags">${utags}</div></div>`:''}
    ${d.media_ignored?`<div class="err" style="color:#7b341e;background:#fff3cd">Image/video attachment ignored — verdict is text-only.</div>`:''}
    ${d.is_spam?`<div class="llm" id="llmBox"><div class="lbl">LLM Threat Analysis</div><div class="llm-wait" id="llmWait">Requesting LLM analysis…</div></div>`:''}
  `;
  document.getElementById('result').style.display='block';
  if(d.is_spam && d.llm_payload) requestLLM(d.llm_payload);
}
async function requestLLM(payload){
  try{
    const resp=await fetch('/llm_analyse',{method:'POST',
      headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
    if(!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const r=await resp.json();
    if(r.error){const el=document.getElementById('llmWait');if(el)el.textContent=r.error;return;}
    const rows=[
      ['Fraud Category', r.fraud_category||'—'],
      ['Urgency',        (r.urgency_level||'—')+(r.urgency_justification?` — ${r.urgency_justification}`:'')],
      ['Impersonating',  r.impersonated_entity||'—'],
      ['Data at Risk',   (r.data_at_risk||[]).join(', ')||'—'],
      ['Attack Method',  r.attack_mechanism||'—'],
      ['Advisory (EN)',  r.victim_advisory||'—'],
      ['Advisory (HI)',  r.advisory_hindi||'—'],
    ].map(([k,v])=>`<div class="llm-row"><div class="llm-k">${k}</div><div class="llm-v">${v}</div></div>`).join('');
    const box=document.getElementById('llmBox');
    if(box) box.innerHTML=`<div class="lbl">LLM Threat Analysis</div>${rows}`;
  }catch(e){
    const el=document.getElementById('llmWait');
    if(el) el.textContent=`LLM error: ${e.message}`;
  }
}
document.addEventListener('keydown',e=>{if((e.ctrlKey||e.metaKey)&&e.key==='Enter')analyse();});
</script>
</body>
</html>"""

# ── Flask app ─────────────────────────────────────────────────────────────────

app = Flask(__name__)
_sms_analyzer = None
_llm_analyzer = None


def _local_ip() -> str:
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]; s.close(); return ip
    except Exception:
        return "127.0.0.1"


@app.route("/")
def index(): return render_template_string(_HTML)


@app.route("/health")
def health():
    return jsonify({
        "status": "ok",
        "model": "indic-bert",
        "threshold": _sms_analyzer.threshold if _sms_analyzer else None,
        "llm": _llm_analyzer.backend_name if _llm_analyzer else "not_configured",
    })


@app.route("/test")
def test():
    if not _sms_analyzer:
        return jsonify({"error": "model not loaded"}), 503
    r = _sms_analyzer.analyze("Your OTP is 123456. Valid 10 mins. Do not share.",
                               {"sender": "VM-SBIBNK"})
    return jsonify({"status": "ok", "is_spam": r["is_spam"],
                    "prob": r["spam_probability"]})


@app.route("/decode_qr", methods=["POST"])
def decode_qr():
    """
    Server-side QR decode for the web-UI demo ONLY (see src/qr_decoder.py
    docstring). On Android the phone decodes on-device via ML Kit and sends
    the already-decoded text — it never uploads the raw image.
    """
    body = request.get_json(force=True, silent=True) or {}
    image_b64 = body.get("image_base64", "")
    if not image_b64:
        return jsonify({"error": "image_base64 field required"}), 400
    try:
        from src.qr_decoder import decode_qr_base64
        decoded = decode_qr_base64(image_b64)
        return jsonify({"decoded": decoded})
    except Exception as e:
        return jsonify({"error": str(e)}), 200


@app.route("/analyse", methods=["POST"])
def analyse():
    body = request.get_json(force=True, silent=True) or {}
    text = body.get("text", "").strip()
    if not text:
        return jsonify({"error": "text field required"}), 400
    if not _sms_analyzer:
        return jsonify({"error": "model not loaded"}), 503
    meta = {}
    if body.get("sender"):          meta["sender"]          = body["sender"]
    if body.get("qr_decoded_text"): meta["qr_decoded_text"] = body["qr_decoded_text"]
    if body.get("media_type"):      meta["media_type"]      = body["media_type"]
    result = _sms_analyzer.analyze(text, meta)
    result.pop("raw_features", None)   # don't send heavy dict to browser
    return jsonify(result)


@app.route("/llm_analyse", methods=["POST"])
def llm_analyse():
    if not _llm_analyzer:
        return jsonify({"error": "LLM not configured (set LLM_BACKEND env var)"}), 200
    payload = request.get_json(force=True, silent=True) or {}
    if not payload:
        return jsonify({"error": "empty payload"}), 400
    try:
        return jsonify(_llm_analyzer.analyze(payload))
    except Exception as e:
        return jsonify({"error": str(e)}), 200


# ── Entry point ───────────────────────────────────────────────────────────────

def main():
    global _sms_analyzer, _llm_analyzer

    ap = argparse.ArgumentParser(description="SMS Spam Detector — Web UI")
    ap.add_argument("--config",   default="configs/config.yaml")
    ap.add_argument("--model",    default="final_model")
    ap.add_argument("--port",     type=int, default=8000)
    ap.add_argument("--host",     default="0.0.0.0")
    ap.add_argument("--quantize", action="store_true")
    ap.add_argument("--tflite",   default=None,
                     help="Path to a model_fp32.tflite / model_int8.tflite from "
                          "src/quantize.py — runs inference through the LiteRT "
                          "runtime instead of the full PyTorch model (mobile-parity test).")
    ap.add_argument("--no-llm",   action="store_true")
    args = ap.parse_args()

    if args.tflite:
        from src.tflite_inference import TFLiteSMSAnalyzer
        print(f"[App] Loading TFLite model from {args.tflite} (tokenizer/threshold from {args.model}) …")
        _sms_analyzer = TFLiteSMSAnalyzer(config_path=args.config, model_path=args.model,
                                          tflite_path=args.tflite)
        print("[App] TFLite model loaded.")
    else:
        from src.inference import SMSAnalyzer
        print(f"[App] Loading model from {args.model} …")
        _sms_analyzer = SMSAnalyzer(config_path=args.config, model_path=args.model,
                                     quantize=args.quantize)
        print("[App] Model loaded.")

    if not args.no_llm:
        backend = os.environ.get("LLM_BACKEND", "")
        if backend:
            try:
                from src.llm_analysis import LLMAnalyzer
                _llm_analyzer = LLMAnalyzer()
                print(f"[App] LLM backend: {_llm_analyzer.backend_name}")
            except Exception as e:
                print(f"[App] LLM setup failed: {e}. Continuing without LLM.")
        else:
            print("[App] LLM_BACKEND not set. Set it to anthropic|openai|gemini|ollama to enable LLM panel.")

    ip = _local_ip()
    print(f"\n{'='*52}")
    print(f"  Server ready.")
    print(f"  Local   :  http://127.0.0.1:{args.port}")
    print(f"  Android :  http://{ip}:{args.port}   ← open this on your phone")
    print(f"  Health  :  http://{ip}:{args.port}/health")
    print(f"  Test    :  http://{ip}:{args.port}/test")
    print(f"{'='*52}\n")

    app.run(host=args.host, port=args.port, debug=False)


if __name__ == "__main__":
    main()
