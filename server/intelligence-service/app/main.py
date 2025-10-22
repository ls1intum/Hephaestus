import os

from fastapi import FastAPI

from app.mentor.router import router as mentor_router
from app.routers.health import router as health_router
from app.routers.detector import router as detector_router

def resolve_version() -> str:
    for key in ("APP_VERSION", "IMAGE_TAG", "GIT_SHA"):
        value = os.getenv(key)
        if value:
            return value
    return "dev"


app = FastAPI(
    title="Hephaestus Intelligence Service API",
    description="API documentation for the Hephaestus Intelligence Service.",
    version=resolve_version(),
    contact={"name": "Felix T.J. Dietrich", "email": "felixtj.dietrich@tum.de"},
)

app.include_router(mentor_router)
app.include_router(health_router)
app.include_router(detector_router)
