from fastapi import FastAPI
from chainlit.utils import mount_chainlit

# from app.routers.mentor import router as mentor_router
from app.routers.health import router as health_router
from app.routers.detector import router as detector_router

app = FastAPI(
    title="Hephaestus Intelligence Service API",
    description="API documentation for the Hephaestus Intelligence Service.",
    version="0.9.0-rc.5",
    contact={"name": "Felix T.J. Dietrich", "email": "felixtj.dietrich@tum.de"},
)

# app.include_router(mentor_router)
app.include_router(health_router)
app.include_router(detector_router)

mount_chainlit(app=app, target="app/chainlit.py", path="/chainlit")