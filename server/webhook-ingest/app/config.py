from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    NATS_URL: str = "localhost"
    NATS_AUTH_TOKEN: str = ""
    GITHUB_WEBHOOK_SECRET: str = ""
    GITLAB_WEBHOOK_SECRET: str = ""

    class Config:
        env_file = ".env"


settings = Settings()
