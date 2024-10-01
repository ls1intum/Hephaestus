from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env")

    OPENAI_API_KEY: str = ""

    AZURE_OPENAI_API_KEY: str = ""
    AZURE_OPENAI_ENDPOINT: str = ""
    AZURE_OPENAI_API_VERSION: str = ""

    @property
    def is_openai_available(self):
        return bool(self.OPENAI_API_KEY)
    
    @property
    def is_azure_openai_available(self):
        return bool(self.AZURE_OPENAI_API_KEY) and bool(self.AZURE_OPENAI_ENDPOINT) and bool(self.AZURE_OPENAI_API_VERSION)

settings = Settings()
