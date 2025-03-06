from fastapi import APIRouter
from pydantic import BaseModel
from langfuse.callback import CallbackHandler
from langchain_core.runnables.config import RunnableConfig

from app.settings import settings
from app.mentor.run import run, start_session

router = APIRouter(prefix="/mentor", tags=["mentor"])


callbacks: List[CallbackHandler] = []
if settings.langfuse_enabled:
    langfuse_handler = CallbackHandler()
    langfuse_handler.auth_check()
    callbacks.append(langfuse_handler)


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
    closed: bool = False


@router.get(
    "/health", summary="Check if the intelligence service is running", status_code=200
)
def status():
    return {"status": "ok"}


@router.post(
    "/start",
    response_model=MentorResponse,
    summary="Start a chat session with an LLM.",
)
def start(request: MentorStartRequest):
    config = RunnableConfig({"configurable": {"thread_id": request.session_id}})
    response = start_session(
        request.previous_session_id, request.user_id, request.dev_progress, config
    )
    response_message = response["messages"][-1].content
    return MentorResponse(content=response_message, closed=response["closed"])


@router.post(
    "/",
    response_model=MentorResponse,
    summary="Continue a chat session with an LLM.",
)
def generate(request: MentorRequest):
    config = RunnableConfig(
        configurable={"thread_id": request.session_id}, callbacks=callbacks
    )
    response = run(request.content, config)
    response_message = response["messages"][-1].content
    return MentorResponse(content=response_message, closed=response["closed"])
