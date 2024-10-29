import os
from langchain.chat_models.base import BaseChatModel
from langchain_openai import ChatOpenAI, AzureChatOpenAI

from .config import settings

model: BaseChatModel

if os.getenv("GITHUB_ACTIONS") == "true":
    # mock model for testing environment
    class MockChatModel(BaseChatModel):
        def invoke(self, message: str):
            return "Mock response"

if settings.is_openai_available:
    model = ChatOpenAI()
elif settings.is_azure_openai_available:
    model = AzureChatOpenAI()
else:
    raise EnvironmentError("No LLM available")
