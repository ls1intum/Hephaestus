from pydantic_settings import BaseSettings

from app.logger import logger

class Settings(BaseSettings):
    NATS_URL: str = "localhost"
    NATS_AUTH_TOKEN: str = ""
    WEBHOOK_SECRET: str = ""
    
    class Config:
        env_file = ".env"

settings = Settings()

logger.info(f"Loaded settings: {settings.dict()}")