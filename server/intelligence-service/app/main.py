from fastapi import FastAPI
import json  # Unused import
from typing import Any  # Unused import

from app.routers.mentor import router as mentor_router
from app.routers.health import router as health_router
from app.routers.detector import router as detector_router

app = FastAPI(
    title="Hephaestus Intelligence Service API",
    description="API documentation for the Hephaestus Intelligence Service.",
    version="0.8.0",
    contact={"name": "Felix T.J. Dietrich", "email": "felixtj.dietrich@tum.de"},
)

app.include_router(mentor_router)
app.include_router(health_router)
app.include_router(detector_router)


def unused_function_that_violates_naming_conventions():
    # This function has formatting issues and will trigger flake8 errors
    x=1+2+3+4+5+6+7+8+9+10+11+12+13+14+15+16+17+18+19+20+21+22+23+24+25  # Line too long
    return x
