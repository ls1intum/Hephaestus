from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    NATS_URL: str = "localhost"
    SECRET: str = ""
    
    class Config:
        env_file = ".env"

settings = Settings()
