import jwt
import chainlit as cl
from typing import Dict, Optional
from app.settings import settings
from app.logger import logger
from app.models import get_model

ChatModel = get_model(settings.MODEL_NAME)


@cl.on_window_message
async def main(message: str):
  await cl.send_window_message("Server: Hello from Chainlit")
  
@cl.header_auth_callback
def header_auth_callback(headers: Dict) -> Optional[cl.User]:
    print(headers)
    # Get access_token from cookie
    cookie = headers.get("cookie")
    if cookie:
        cookies = cookie.split(";")
        for c in cookies:
            if "access_token" in c:
                access_token = c.split("=")[1]
                break
    else:
        access_token = None
    
    if not access_token:
        return None
    
    decoded = jwt.decode(access_token, options={"verify_signature": False}) 
    if not decoded:
        return None
    
    print(f"Decoded JWT: {decoded}")
    identifier = decoded.get("sub") or decoded.get("identifier")
    if not identifier:
        return None
    display_name = decoded.get("name") or decoded.get("display_name")
    if not display_name:
        return None
    return cl.User(identifier=identifier, display_name=display_name, metadata=decoded)
  
  


# @cl.oauth_callback
# def oauth_callback(
#   provider_id: str,
#   token: str,
#   raw_user_data: Dict[str, str],
#   default_user: cl.User,
# ) -> Optional[cl.User]:
#     logger.info(f"OAuth callback for provider {provider_id} with token {token}")
#     return default_user

from langchain_openai import ChatOpenAI
from langchain.prompts import ChatPromptTemplate
from langchain.schema import StrOutputParser
from langchain.schema.runnable import Runnable
from langchain.schema.runnable.config import RunnableConfig
from typing import cast

import chainlit as cl


@cl.on_chat_start
async def on_chat_start():
    model = ChatModel(streaming=True)
    prompt = ChatPromptTemplate.from_messages(
        [
            (
                "system",
                "You're a very knowledgeable historian who provides accurate and eloquent answers to historical questions.",
            ),
            ("human", "{question}"),
        ]
    )
    runnable = prompt | model | StrOutputParser()
    cl.user_session.set("runnable", runnable)


@cl.on_message
async def on_message(message: cl.Message):
    runnable = cast(Runnable, cl.user_session.get("runnable"))  # type: Runnable

    msg = cl.Message(content="")

    async for chunk in runnable.astream(
        {"question": message.content},
        config=RunnableConfig(callbacks=[cl.LangchainCallbackHandler()]),
    ):
        await msg.stream_token(chunk)

    await msg.send()