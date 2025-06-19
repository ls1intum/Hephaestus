from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.mentor.router import router as mentor_router
from app.routers.health import router as health_router
from app.routers.detector import router as detector_router

app = FastAPI(
    title="Hephaestus Intelligence Service API",
    description="API documentation for the Hephaestus Intelligence Service.",
    version="0.9.0-rc.5",
    contact={"name": "Felix T.J. Dietrich", "email": "felixtj.dietrich@tum.de"},
)

# TODO: REMOVE
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production, replace with specific origins
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(mentor_router)
app.include_router(health_router)
app.include_router(detector_router)
