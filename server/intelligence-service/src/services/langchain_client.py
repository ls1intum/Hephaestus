from langchain_openai import OpenAI
from langchain_community.chat_models import AzureChatOpenAI
from ..config import settings

def get_openai_client():
    if settings.OPENAI_API_KEY:
        return OpenAI(api_key=settings.OPENAI_API_KEY)
    elif settings.AZURE_OPENAI_API_KEY:
        return AzureChatOpenAI(
            api_key=settings.AZURE_OPENAI_API_KEY,
            endpoint=settings.AZURE_OPENAI_ENDPOINT,
            api_version=settings.AZURE_OPENAI_API_VERSION
        )
    else:
        raise ValueError("No valid OpenAI configuration found.")