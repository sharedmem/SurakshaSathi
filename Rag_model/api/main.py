"""FastAPI entrypoint. This is the only thing the Android app talks to -- everything else
(clustering, crawlers, alerting) runs as background Cloud Run Jobs, not HTTP routes here, per the
latency-isolation note in docs/ARCHITECTURE.md (a 25s APK scan or a daily DBSCAN pass must not
share timeout/autoscaling config with the sub-1.5s message-scan path).
"""

from fastapi import FastAPI

from api.routes.apk_scan import router as apk_scan_router
from api.routes.dashboard import router as dashboard_router
from api.routes.guardian_chat import router as guardian_chat_router
from api.routes.message_scan import router as message_scan_router
from api.routes.user_registration import router as user_registration_router

app = FastAPI(title="SurakshaSathi RAG Backend", version="0.1.0")

app.include_router(message_scan_router)
app.include_router(apk_scan_router)
app.include_router(dashboard_router)
app.include_router(guardian_chat_router)
app.include_router(user_registration_router)


@app.get("/healthz")
async def healthz():
    return {"status": "ok"}
