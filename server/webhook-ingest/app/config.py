from fastapi import logger
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    NATS_URL: str = "localhost"
    NATS_AUTH_TOKEN: str = ""
    WEBHOOK_SECRET: str = ""
    
    class Config:
        env_file = ".env"

settings = Settings()

logger.info(f"Loaded settings: {settings.model_dump_json()}")