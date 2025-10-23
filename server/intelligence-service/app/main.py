from fastapi import FastAPI

from app.mentor.router import router as mentor_router
from app.routers.health import router as health_router
from app.routers.detector import router as detector_router

app = FastAPI(
    title="Hephaestus Intelligence Service API",
    description="API documentation for the Hephaestus Intelligence Service.",
    version="0.9.3-rc.1",
    contact={"name": "Felix T.J. Dietrich", "email": "felixtj.dietrich@tum.de"},
)

app.include_router(mentor_router)
app.include_router(health_router)
app.include_router(detector_router)
