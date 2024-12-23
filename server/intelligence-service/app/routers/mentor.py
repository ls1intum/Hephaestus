from typing import List
from fastapi import APIRouter
from pydantic import BaseModel

from langchain_core.messages import HumanMessage, AIMessage
from langchain_core.runnables.config import RunnableConfig

from ..mentor.run import run


router = APIRouter(prefix="/mentor", tags=["mentor"])


class MentorRequest(BaseModel):
    session_id: str
    content: str


class MentorResponce(BaseModel):
    content: str


@router.post(
    "/",
    response_model=MentorResponce,
    summary="Start and continue a chat session with an LLM.",
)
async def generate(request: MentorRequest):
    config = RunnableConfig({"configurable": {"thread_id": request.session_id}})
    response = await run(request.content, config)
    response_message = response["messages"][-1].content
    return MentorResponce(content=response_message)
