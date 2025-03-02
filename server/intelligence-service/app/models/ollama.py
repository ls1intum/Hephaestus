from functools import partialmethod
from typing import Sequence
from requests.auth import _basic_auth_str as basic_auth_str
from ollama import Client
from langchain_ollama.chat_models import ChatOllama

from app.logger import logger
from app.settings import settings
from app.models.model_provider import ModelProvider


client = Client(
    host=settings.OLLAMA_HOST,
    headers={
        "Authorization": basic_auth_str(
            settings.OLLAMA_BASIC_AUTH_USERNAME, settings.OLLAMA_BASIC_AUTH_PASSWORD
        )
    },
)


class OllamaProvider(ModelProvider):
    models: Sequence[str] = []

    def get_name(self) -> str:
        return "ollama"

    def validate_provider(self):
        if (
            not settings.OLLAMA_BASIC_AUTH_USERNAME
            or not settings.OLLAMA_BASIC_AUTH_PASSWORD
        ):
            raise EnvironmentError("Ollama basic auth credentials not found")
        if not settings.OLLAMA_HOST:
            raise EnvironmentError("Ollama host not found")

        data = client._request(
            dict,
            "GET",
            "/api/version",
        )
        if data.get("version") is None:
            raise EnvironmentError(
                "Ollama version not found, check the Ollama configuration"
            )
        logger.info(f"Ollama version: {data['version']}")

    def validate_model_name(self, model_name: str):
        if not self.models:
            models = client.list().models
            self.models = [model.model for model in models]
            logger.info(f"Available Ollama models: {self.models}")
        if model_name not in self.models:
            raise EnvironmentError(
                f"Model '{model_name}' not found. Available models: {self.models}"
            )

    def get_model(self, model_name: str):
        class ChatModel(ChatOllama):
            __init__ = partialmethod(
                ChatOllama.__init__,
                model=model_name,
                base_url=settings.OLLAMA_HOST,
                client_kwargs={
                    "headers": {
                        "Authorization": basic_auth_str(
                            settings.OLLAMA_BASIC_AUTH_USERNAME,
                            settings.OLLAMA_BASIC_AUTH_PASSWORD,
                        )
                    }
                },
            )

        return ChatModel


ollama_provider = OllamaProvider()
