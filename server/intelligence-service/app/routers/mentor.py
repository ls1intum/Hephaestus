from typing import List
from fastapi import APIRouter
from pydantic import BaseModel

from langchain_core.messages import HumanMessage, AIMessage

from ..mentor.run import graph


router = APIRouter(prefix="/mentor", tags=["mentor"])


class Message(BaseModel):
    sender: str
    content: str


class MessageHistory(BaseModel):
    messages: List[Message]


class MentorMessage(BaseModel):
    content: str


@router.post(
    "/",
    response_model=MentorMessage,
    summary="Start and continue a chat session with an LLM.",
)
def generate(request: MessageHistory):
    messages = []
    for message in request.messages:
        if message.content:
            if message.sender == "USER":
                messages.append(HumanMessage(content=message.content))
            else:
                messages.append(AIMessage(content=message.content))
    response_message = graph.invoke({"messages": messages})["messages"][-1].content
    return MentorMessage(content=response_message)
