from functools import partialmethod
from typing import Sequence
import openai
from langchain_openai.chat_models import ChatOpenAI

from app.settings import settings
from app.logger import logger
from app.models.model_provider import ModelProvider


class OpenAIProvider(ModelProvider):
    models: Sequence[str] = []

    def get_name(self) -> str:
        return "openai"

    def validate_provider(self):
        if not settings.OPENAI_API_KEY:
            raise EnvironmentError("OpenAI API key not found")

        openai.api_type = "openai"
        models = openai.models.list().data
        self.models = [model.id for model in models]

    def validate_model_name(self, model_name: str):
        if not self.models:
            openai.api_type = "openai"
            models = openai.models.list().data
            self.models = [model.id for model in models]
            logger.info(f"Available OpenAI models: {self.models}")
        if model_name not in self.models:
            raise EnvironmentError(
                f"Model '{model_name}' not found. Available models: {self.models}"
            )

    def get_model(self, model_name: str):
        class ChatModel(ChatOpenAI):
            __init__ = partialmethod(
                ChatOpenAI.__init__,
                model=model_name,
            )

        return ChatModel


openai_provider = OpenAIProvider()
