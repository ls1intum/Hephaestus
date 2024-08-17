from pydantic_settings import BaseSettings
from app.logger import logger


class Settings(BaseSettings):
    NATS_URL: str = "localhost"
    NATS_USER: str = ""
    NATS_PASSWORD: str = ""
    WEBHOOK_SECRET: str = ""
    
    class Config:
        env_file = ".env"

settings = Settings()

logger.info(f"Loaded settings: {settings.model_dump_json()}")