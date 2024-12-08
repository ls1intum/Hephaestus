import os
from langchain.chat_models.base import BaseChatModel
from langchain_community.chat_models.fake import FakeListChatModel
from langchain_openai import ChatOpenAI, AzureChatOpenAI

from .settings import settings

temperature = 0.7
max_tokens = 4096

model: BaseChatModel

if os.getenv("GITHUB_ACTIONS") == "true":
    model = FakeListChatModel(responses=["Response 1", "Response 2"])
else:
    
    if settings.is_openai_available:
        Model = ChatOpenAI
    elif settings.is_azure_openai_available:
        Model = AzureChatOpenAI
    else:
        raise EnvironmentError("No LLM available")

    model = Model(temperature=temperature, max_tokens=max_tokens)