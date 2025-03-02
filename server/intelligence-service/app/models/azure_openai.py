import requests
from functools import partialmethod
from typing import Sequence
from langchain_openai.chat_models import AzureChatOpenAI

from app.settings import settings
from app.logger import logger
from app.models.model_provider import ModelProvider


class AzureOpenAIProvider(ModelProvider):
    deployments: Sequence[str] = []

    def get_name(self) -> str:
        return "azure_openai"

    def validate_provider(self):
        if not settings.AZURE_OPENAI_API_KEY:
            raise EnvironmentError("Azure OpenAI API key not found")
        if not settings.AZURE_OPENAI_ENDPOINT:
            raise EnvironmentError("Azure OpenAI endpoint not found")
        if not settings.OPENAI_API_VERSION:
            raise EnvironmentError(
                "OpenAI API version not found, required for Azure OpenAI"
            )

        # If this breaks in the future we have to use azure-mgmt-cognitiveservices which needs 6 additional
        # environment variables
        base_url = f"{settings.AZURE_OPENAI_ENDPOINT}/openai"
        headers = {"api-key": settings.AZURE_OPENAI_API_KEY}
        models_response = requests.get(
            f"{base_url}/models?api-version=2023-03-15-preview",
            headers=headers,
            timeout=30,
        )
        models_data = models_response.json()["data"]
        deployments_response = requests.get(
            f"{base_url}/deployments?api-version=2023-03-15-preview",
            headers=headers,
            timeout=30,
        )
        deployments_data = deployments_response.json()["data"]

        # Check if deployment["model"] is a substring of model["id"], i.e. "gpt-4o" is substring "gpt-4o-2024-05-13"
        chat_completion_models = ",".join(
            model["id"]
            for model in models_data
            if model["capabilities"]["chat_completion"]
        )
        self.deployments = [
            deployment["id"]
            for deployment in deployments_data
            if deployment["model"] in chat_completion_models
        ]
        logger.info(f"Available Azure-OpenAI model deployments: {self.deployments}")

    def validate_model_name(self, model_name: str):
        if not self.deployments:
            self.validate_provider()
        if model_name not in self.deployments:
            raise EnvironmentError(
                f"Model deployment '{model_name}' not found. Available deployments: {self.deployments}"
            )

    def get_model(self, model_name: str):
        class ChatModel(AzureChatOpenAI):
            __init__ = partialmethod(
                AzureChatOpenAI.__init__,
                azure_deployment=model_name,
            )

        return ChatModel


azure_openai_provider = AzureOpenAIProvider()
