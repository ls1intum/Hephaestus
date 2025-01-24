from typing import List
from fastapi import APIRouter
from pydantic import BaseModel
from langchain_core.runnables.config import RunnableConfig
from ..mentor.run import run, start_session


router = APIRouter(prefix="/mentor", tags=["mentor"])


class MentorStartRequest(BaseModel):
    session_id: str
    user_id: str
    previous_session_id: str
    dev_progress: str


class MentorRequest(BaseModel):
    session_id: str
    content: str


class MentorResponse(BaseModel):
    content: str


@router.post(
    "/start",
    response_model=MentorResponse,
    summary="Start a chat session with an LLM.",
)
def start(request: MentorStartRequest):
    config = RunnableConfig({"configurable": {"thread_id": request.session_id}})
    response = start_session(request.previous_session_id, request.dev_progress, request.user_id, config)
    response_message = response["messages"][-1].content
    return MentorResponse(content=response_message)


@router.post(
    "/",
    response_model=MentorResponse,
    summary="Continue a chat session with an LLM.",
)
def generate(request: MentorRequest):
    config = RunnableConfig({"configurable": {"thread_id": request.session_id}})
    response = run(request.content, config)
    response_message = response["messages"][-1].content
    return MentorResponse(content=response_message)
