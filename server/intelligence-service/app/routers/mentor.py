from typing import List
from fastapi import APIRouter
from pydantic import BaseModel
from langchain_core.runnables.config import RunnableConfig
from ..mentor.run import run, start_session


router = APIRouter(prefix="/mentor", tags=["mentor"])


class MentorStartRequest(BaseModel):
    session_id: str
    previous_session_id: str


class MentorRequest(BaseModel):
    session_id: str
    content: str


class MentorResponce(BaseModel):
    content: str


@router.post(
    "/start",
    response_model=MentorResponce,
    summary="Start a chat session with an LLM.",
)
def start(request: MentorStartRequest):
    config = RunnableConfig({"configurable": {"thread_id": request.session_id}})
    response = start_session(request.previous_session_id, config)
    response_message = response["messages"][-1].content
    return MentorResponce(content=response_message)


@router.post(
    "/",
    response_model=MentorResponce,
    summary="Continue a chat session with an LLM.",
)
def generate(request: MentorRequest):
    config = RunnableConfig({"configurable": {"thread_id": request.session_id}})
    response = run(request.content, config)
    response_message = response["messages"][-1].content
    return MentorResponce(content=response_message)
