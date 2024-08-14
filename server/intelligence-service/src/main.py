from fastapi import FastAPI

from .auth.router import router
from .config import settings

app = FastAPI(title=settings.APP_NAME)

app.include_router(router)
