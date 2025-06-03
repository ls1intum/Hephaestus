import chainlit as cl
from chainlit.types import ThreadDict
from typing import Optional, Dict


@cl.header_auth_callback
def header_auth_callback(headers: Dict) -> Optional[cl.User]:
  # Verify the signature of a token in the header (ex: jwt token)
  # or check that the value is matching a row from your database
  # if headers.get("test-header") == "test-value":
  return cl.User(identifier="admin", metadata={"role": "admin", "provider": "header"})
  # else:
    # return None

@cl.on_chat_start
async def main():
    await cl.Message(content="Hello World").send()
    
@cl.on_message
def on_message(msg: cl.Message):
    print("The user sent: ", msg.content)

@cl.on_stop
def on_stop():
    print("The user wants to stop the task!")

@cl.on_chat_end
def on_chat_end():
    print("The user disconnected!")


@cl.on_chat_resume
async def on_chat_resume(thread: ThreadDict):
    print("The user resumed a previous chat session!")    
