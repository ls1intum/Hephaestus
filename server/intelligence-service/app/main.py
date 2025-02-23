from fastapi import FastAPI
from .routers.mentor import router as mentor_router
from .routers.health import router as health_router
from .routers.analysis import router as analysis_router

app = FastAPI(
    title="Hephaestus Intelligence Service API",
    description="API documentation for the Hephaestus Intelligence Service.",
    version="0.0.1",
    contact={"name": "Felix T.J. Dietrich", "email": "felixtj.dietrich@tum.de"},
)

app.include_router(mentor_router)
app.include_router(health_router)
app.include_router(analysis_router)
