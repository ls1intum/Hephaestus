from dotenv import load_dotenv
from pydantic_settings import BaseSettings, SettingsConfigDict

load_dotenv()


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env")

    OPENAI_API_KEY: str = ""

    AZURE_OPENAI_API_KEY: str = ""
    AZURE_OPENAI_ENDPOINT: str = ""
    AZURE_OPENAI_API_VERSION: str = ""

    MODEL_NAME: str = "gpt-4o"
    MODEL_TEMPERATURE: float = 0.7
    MODEL_MAX_TOKENS: int = 4096

    @property
    def is_openai_available(self):
        return bool(self.OPENAI_API_KEY)

    @property
    def is_azure_openai_available(self):
        return (
            bool(self.AZURE_OPENAI_API_KEY)
            and bool(self.AZURE_OPENAI_ENDPOINT)
            and bool(self.AZURE_OPENAI_API_VERSION)
        )


settings = Settings()
