from typing import List, Any

from langchain_core.language_models.fake_chat_models import FakeChatModel
from langchain_core.language_models.chat_models import BaseChatModel
from langchain.chat_models import init_chat_model

from app.models.model_provider import ModelProvider
from app.models.openai import openai_provider
from app.models.azure_openai import azure_openai_provider
from app.models.ollama import ollama_provider
from app.models.fake import fake_provider


class HephaestusFakeChatModel(FakeChatModel):
    """Fake chat model that safely supports tool binding in our pipeline."""

    # LangChain's FakeChatModel may not implement bind_tools; provide a no-op.
    def bind_tools(self, tools, tool_choice: str | None = None):  # type: ignore[override]
        return self


def init_hephaestus_chat_model(
    model_name: str,
    **kwargs: Any,
) -> BaseChatModel:
    """Initialize a chat model; use a FakeChatModel when prefix is 'fake:'.

    Examples:
        - 'fake:model' -> FakeChatModel()
        - 'openai:gpt-4o' -> delegated to init_chat_model
    """
    prefix = model_name.split(":", 1)[0].lower() if model_name else ""
    if prefix == "fake":
        return HephaestusFakeChatModel()
    return init_chat_model(model_name, **kwargs)


model_providers: List[ModelProvider] = [
    openai_provider,
    azure_openai_provider,
    ollama_provider,
    fake_provider,
]


def get_model(model_name: str):
    """
    Loads and returns the chat model based on the given model_name.

    The model_name should be in the format "provider:model", where:
      - provider: Identifier for the model provider (e.g., "openai", "azure_openai", etc.)
      - model: The specific model name within that provider

    Raises:
        EnvironmentError: If model_name is empty or the provider is not found.
        ValueError: If model_name is not in the correct format.
    """
    if not model_name:
        raise ValueError("model_name is not set")

    try:
        provider_name, actual_model_name = model_name.split(":", 1)
    except ValueError:
        raise ValueError("model_name must be in the format 'provider:model'")

    for provider in model_providers:
        if provider.get_name() == provider_name:
            break
    else:
        raise EnvironmentError(
            f"Model provider '{provider_name}' not found in {model_providers}"
        )

    provider.validate_provider()
    provider.validate_model_name(actual_model_name)

    ChatModel = provider.get_model(actual_model_name)
    return ChatModel
