"""Feature Map + Alerts endpoints -- the HTTP surface over alerting/heatmap.py,
alerting/region_alerts.py, and alerting/fcm_sender.py that the Android app's frauddashboard
feature and Alerts tab actually call. Everything here reads from the same Blue DB tables the
batch jobs (alerting/daily_job.py, crawlers/cybercrime_digest_job.py) write to -- swapping the
synthetic demo seed for real traffic later requires no changes here.
"""

from calendar import monthrange
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel
from sqlalchemy import func

from alerting.region_alerts import active_campaigns_for_region, build_daily_alerts
from clustering.region_weighting import REGIONS
from config.settings import settings
from db.postgres_client import Message, NationalDigest, get_session_factory

router = APIRouter(prefix="/v1")

DEFAULT_WINDOW_DAYS = 7  # trailing window default, per spec ("past 7 days data")
HEATMAP_MEDIUM_AT = 15
HEATMAP_HIGH_AT = 40


class HeatmapEntry(BaseModel):
    region: str
    lat: float
    lon: float
    message_count: int
    digest_count: int
    total: int
    severity: str  # "low" | "medium" | "high"
    trend_vs_previous_window: float  # e.g. +0.35 == 35% up vs the prior equal-length window


class HeatmapResponse(BaseModel):
    window_label: str
    entries: list[HeatmapEntry]


class CampaignResponse(BaseModel):
    region: str
    target_persona: str
    lure_label: str
    malicious_apk_theme: str
    intervention: str
    message_count: int


class RegionalAlertResponse(BaseModel):
    id: str
    region: str
    is_nearby: bool
    source: str
    severity: str
    fraud_type: str
    body: str
    report_count: int
    timestamp: datetime


def _severity(total: int) -> str:
    if total >= HEATMAP_HIGH_AT:
        return "high"
    if total >= HEATMAP_MEDIUM_AT:
        return "medium"
    return "low"


def _window_bounds(window_days: int | None, month: str | None) -> tuple[datetime, datetime, datetime, datetime, str]:
    """Returns (start, end, prev_start, prev_end, label) -- `end` is exclusive."""
    if month:
        try:
            year, mon = (int(p) for p in month.split("-", 1))
        except ValueError:
            raise HTTPException(400, "month must be formatted YYYY-MM")
        start = datetime(year, mon, 1, tzinfo=timezone.utc)
        days_in_month = monthrange(year, mon)[1]
        end = start + timedelta(days=days_in_month)
        prev_end = start
        prev_start = prev_end - (end - start)
        return start, end, prev_start, prev_end, start.strftime("%B %Y")

    days = window_days or DEFAULT_WINDOW_DAYS
    end = datetime.now(timezone.utc)
    start = end - timedelta(days=days)
    prev_start = start - timedelta(days=days)
    prev_end = start
    return start, end, prev_start, prev_end, f"Last {days} days"


def _region_counts(session, model, region_col, start: datetime, end: datetime, count_col=None) -> dict[str, int]:
    ts_col = model.timestamp if hasattr(model, "timestamp") else model.date
    query = session.query(region_col, func.count() if count_col is None else func.sum(count_col))
    query = query.filter(ts_col >= start, ts_col < end, region_col.isnot(None))
    return {region: int(count or 0) for region, count in query.group_by(region_col).all()}


@router.get("/heatmap", response_model=HeatmapResponse)
async def get_heatmap(
    window_days: int | None = Query(None, ge=1, le=180),
    month: str | None = Query(None, description="YYYY-MM, overrides window_days"),
    region: str | None = Query(None),
) -> HeatmapResponse:
    if not settings.postgres_dsn:
        raise HTTPException(503, "POSTGRES_DSN not configured")

    start, end, prev_start, prev_end, label = _window_bounds(window_days, month)
    Session = get_session_factory()
    with Session() as session:
        msg_counts = _region_counts(session, Message, Message.region, start, end)
        digest_counts = _region_counts(session, NationalDigest, NationalDigest.region, start, end, NationalDigest.report_count)
        prev_msg_counts = _region_counts(session, Message, Message.region, prev_start, prev_end)
        prev_digest_counts = _region_counts(session, NationalDigest, NationalDigest.region, prev_start, prev_end, NationalDigest.report_count)

    # Every known state/UT is included, even with zero activity -- the Feature Map must be able
    # to show "0 alerts" for a quiet state rather than omitting it from the picker/map entirely.
    regions = set(REGIONS) - {"Unknown"}
    if region:
        regions &= {region}

    entries = []
    for r in regions:
        lat, lon, _ = REGIONS.get(r, REGIONS["Unknown"])
        msg_c = msg_counts.get(r, 0)
        digest_c = digest_counts.get(r, 0)
        total = msg_c + digest_c
        prev_total = prev_msg_counts.get(r, 0) + prev_digest_counts.get(r, 0)
        trend = (total - prev_total) / prev_total if prev_total > 0 else (1.0 if total > 0 else 0.0)
        entries.append(
            HeatmapEntry(
                region=r,
                lat=lat,
                lon=lon,
                message_count=msg_c,
                digest_count=digest_c,
                total=total,
                severity=_severity(total),
                trend_vs_previous_window=round(trend, 3),
            )
        )

    entries.sort(key=lambda e: e.total, reverse=True)
    return HeatmapResponse(window_label=label, entries=entries)


@router.get("/campaigns", response_model=list[CampaignResponse])
async def get_campaigns(region: str = Query(...)) -> list[CampaignResponse]:
    if not settings.postgres_dsn:
        raise HTTPException(503, "POSTGRES_DSN not configured")

    Session = get_session_factory()
    with Session() as session:
        campaigns = active_campaigns_for_region(session, region)

    return [
        CampaignResponse(
            region=c.region,
            target_persona=c.target_persona,
            lure_label=c.lure_label,
            malicious_apk_theme=c.malicious_apk_theme,
            intervention=c.intervention,
            message_count=c.message_count,
        )
        for c in campaigns
    ]


@router.get("/alerts/daily", response_model=list[RegionalAlertResponse])
async def get_daily_alerts(
    region: str = Query(...),
    persona: str | None = Query(None),
    include_nearby: bool = Query(True),
) -> list[RegionalAlertResponse]:
    if not settings.postgres_dsn:
        raise HTTPException(503, "POSTGRES_DSN not configured")

    Session = get_session_factory()
    with Session() as session:
        alerts = build_daily_alerts(session, region, persona, include_nearby)

    return [
        RegionalAlertResponse(
            id=a.id,
            region=a.region,
            is_nearby=a.is_nearby,
            source=a.source,
            severity=a.severity,
            fraud_type=a.fraud_type,
            body=a.body,
            report_count=a.report_count,
            timestamp=a.timestamp,
        )
        for a in alerts
    ]
