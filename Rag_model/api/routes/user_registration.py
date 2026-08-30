"""POST /v1/users/register -- called once from the Android app's Registration screen (phone,
email, persona, language), right after Permissions on a first install (see
SurakshaSathi's SurakshaSathiNavHost.kt for the full first-run flow).

Upserts into the SAME Blue DB `user_map` table the clustering pipeline (clustering/persona.py),
alerting job (alerting/daily_job.py), and seed script (db/seed/seed_blue_db.py) already read and
write -- that table already had exactly the shape this needs (user_id/phone/persona/region), it
just had no real write path from the app before now. No separate database is required: this reuses
the existing `postgres_dsn` Cloud SQL instance. If a genuinely separate instance (e.g. a dedicated
Azure database) is later wanted for this table, point `settings.users_db_dsn` at it -- see
`_get_users_session_factory` below -- and every call site here is unchanged.
"""

from fastapi import APIRouter
from pydantic import BaseModel

router = APIRouter()


class UserRegistrationRequest(BaseModel):
    phone: str
    email: str
    persona: str
    language: str = "en"
    display_name: str | None = None


class UserRegistrationResponse(BaseModel):
    user_id: str
    status: str  # "REGISTERED" | "REGISTERED_LOCAL_ONLY"


def _get_users_session_factory():
    """Falls back to the main Blue DB instance (`postgres_dsn`) when no dedicated
    `users_db_dsn` is configured -- see module docstring. Both point at the same `UserMap` table
    shape either way; the DSN choice is purely which physical instance owns it."""
    from config.settings import settings

    dsn = settings.users_db_dsn or settings.postgres_dsn
    if not dsn:
        return None

    from sqlalchemy import create_engine
    from sqlalchemy.orm import sessionmaker

    dsn_normalized = dsn.replace("postgresql://", "postgresql+psycopg://", 1) if dsn.startswith("postgresql://") else dsn
    engine = create_engine(dsn_normalized, connect_args={"connect_timeout": 3})
    return sessionmaker(bind=engine)


def _upsert_user_blocking(req: UserRegistrationRequest) -> bool:
    """Returns True if the row was actually written server-side, False if no DB is configured
    (registration still succeeds from the client's point of view -- the account already exists on
    the device via UserPreferencesDataStore.completeRegistration regardless)."""
    Session = _get_users_session_factory()
    if Session is None:
        return False

    from db.postgres_client import UserMap

    with Session() as session:
        session.merge(
            UserMap(
                user_id=req.phone,
                display_name=req.display_name,
                phone_number=req.phone,
                email=req.email,
                persona=req.persona,
                preferred_language=req.language,
            )
        )
        session.commit()
    return True


@router.post("/v1/users/register", response_model=UserRegistrationResponse)
async def register_user(req: UserRegistrationRequest) -> UserRegistrationResponse:
    import asyncio

    written = await asyncio.to_thread(_upsert_user_blocking, req)
    return UserRegistrationResponse(
        user_id=req.phone,
        status="REGISTERED" if written else "REGISTERED_LOCAL_ONLY",
    )
