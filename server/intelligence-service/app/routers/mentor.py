import json
from typing import List, Optional, Union, Any, Dict
from pydantic import BaseModel, Field
from fastapi import Query
from fastapi.responses import StreamingResponse
from fastapi import APIRouter
from langfuse.callback import CallbackHandler
from langchain_core.runnables.config import RunnableConfig
from langchain_core.output_parsers import StrOutputParser
from langchain_core.messages import HumanMessage, AIMessage, SystemMessage
from app.models import get_model
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


#############

ChatModel = get_model(settings.MODEL_NAME)
model = ChatModel(temperature=0.7, max_tokens=4096)

class ClientAttachment(BaseModel):
    name: Optional[str] = None
    contentType: Optional[str] = None  
    url: str

class ToolCallDetail(BaseModel):
    type: str
    function: Optional[Dict[str, str]] = None  # Simplified from Any to str

class ToolCall(BaseModel):
    id: str
    type: str = "function"
    function: Dict[str, str]  # Simplified from Any to str

class ToolResult(BaseModel):
    toolCallId: str
    result: str  # Changed from Any to str to avoid OpenAPI generator issues

class ToolInvocation(BaseModel):
    """Simplified ToolInvocation model to avoid OpenAPI generator issues"""
    state: str  # 'partial-call' | 'call' | 'result'
    step: Optional[int] = None
    toolCallId: Optional[str] = None
    toolName: Optional[str] = None
    args: Optional[str] = None  # Changed from Dict[str, Any] to str to avoid complex types
    result: Optional[str] = None  # Changed from Any to str to avoid complex types

class ReasoningDetail(BaseModel):
    type: str  # 'text' | 'redacted'
    text: Optional[str] = None
    signature: Optional[str] = None
    data: Optional[str] = None

class TextUIPart(BaseModel):
    type: str = Field(default="text")
    text: str

class ReasoningUIPart(BaseModel):
    type: str = Field(default="reasoning")
    reasoning: str
    details: List[ReasoningDetail] = Field(default_factory=list)

class ToolInvocationUIPart(BaseModel):
    type: str = Field(default="tool-invocation")
    toolInvocation: ToolInvocation

class SourceUIPart(BaseModel):
    type: str = Field(default="source")
    source: Dict[str, str]  # Simplified from Any to str

class FileUIPart(BaseModel):
    type: str = Field(default="file")
    mimeType: str
    data: str

class StepStartUIPart(BaseModel):
    type: str = Field(default="step-start")

# Union type for all UI parts - EXACT match for Vercel AI SDK
UIPart = Union[TextUIPart, ReasoningUIPart, ToolInvocationUIPart, SourceUIPart, FileUIPart, StepStartUIPart]

class ClientMessage(BaseModel):
    """PERFECT MATCH for actual frontend payload: {role=user, content=hello, parts=[{type=text, text=hello}]}"""
    role: str           # REQUIRED: 'user' | 'assistant' | 'system'
    content: str        # REQUIRED: Text content
    
    # Optional fields - ALL simplified to avoid Any types
    parts: Optional[List[Dict[str, str]]] = None  # Simplified: parts array
    id: Optional[str] = None
    createdAt: Optional[str] = None
    reasoning: Optional[str] = None
    experimental_attachments: Optional[List[ClientAttachment]] = None
    data: Optional[str] = None  # Simplified from JSONValue to str
    annotations: Optional[List[str]] = None  # Simplified from JSONValue to str
    toolInvocations: Optional[List[ToolInvocation]] = None
    
    class Config:
        extra = "allow"

class Request(BaseModel):
    messages: List[ClientMessage]


def convert_to_langchain_messages(client_messages: List[ClientMessage]):
    """Convert ClientMessage objects to LangChain message objects."""
    langchain_messages = []
    
    for msg in client_messages:
        role = msg.role.lower()
        content = msg.content
        
        if role == "user":
            langchain_messages.append(HumanMessage(content=content))
        elif role == "assistant":
            langchain_messages.append(AIMessage(content=content))
        elif role == "system":
            langchain_messages.append(SystemMessage(content=content))
        else:
            # Default to human message for unknown roles
            langchain_messages.append(HumanMessage(content=content))
    
    return langchain_messages

class EventStreamResponse(StreamingResponse):
    media_type = "text/event-stream"


@router.post(
    "/chat",
    responses={
        200: {
            "description": "Data stream for Vercel AI SDK",
            "content": {
                "text/plain": { 
                    "examples": {
                        "first-chunk": {
                            "summary": "Typical data stream frame",
                            "value": '0:"Hello World!"'
                        }
                    }
                }
            },
            "headers": {              
                "x-vercel-ai-data-stream": {
                    "description": "Protocol version for Vercel AI streaming",
                    "schema": {"type": "string", "example": "v1"}
                }
            },
        }
    }
)
async def handle_chat_data(request: Request):
    messages = request.messages
    
    def generate():
        # Convert ClientMessage objects to LangChain message objects
        langchain_messages = convert_to_langchain_messages(messages)
        
        chain = model | StrOutputParser()
        for chunk in chain.stream(langchain_messages):
            # Skip empty chunks to avoid JSON parsing issues
            yield '0:{text}\n'.format(text=json.dumps(chunk))
        # Use json.dumps with compact formatting (no spaces)
        yield 'd:{"finishReason":"stop"}\n'
    
    response = StreamingResponse(generate(), media_type="text/plain")
    response.headers['x-vercel-ai-data-stream'] = 'v1'
    return response