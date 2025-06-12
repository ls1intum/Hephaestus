import json
from typing import List, Literal, Optional, Union, Any, Dict
from pydantic import BaseModel, Field
from fastapi import Query
from fastapi.responses import StreamingResponse
from fastapi import APIRouter
from langchain_core.output_parsers import StrOutputParser
from langchain_core.messages import HumanMessage, AIMessage, SystemMessage
from app.models import get_model
from app.settings import settings


router = APIRouter(prefix="/mentor", tags=["mentor"])

ChatModel = get_model(settings.MODEL_NAME)
model = ChatModel(temperature=0.7, max_tokens=4096)


class TextUIPart(BaseModel):
    """A text part of a message."""

    type: Literal["text"] = "text"
    text: str


class ReasoningOpenDetail(BaseModel):
    type: Literal["text"] = "text"
    text: str
    signature: Optional[str] = None


class ReasoningRedactedDetail(BaseModel):
    type: Literal["redacted"] = "redacted"
    data: str


ReasoningDetail = Union[ReasoningOpenDetail, ReasoningRedactedDetail]


class ReasoningUIPart(BaseModel):
    """A reasoning part of a message."""

    type: Literal["reasoning"] = "reasoning"
    reasoning: str = Field(..., description="The reasoning text")
    details: List[ReasoningDetail] = Field(default_factory=list)


class ToolCall(BaseModel):
    state: Literal["call"] = "call"
    step: Optional[int] = None
    toolCallId: str
    toolName: str
    args: Dict[str, Any]


class ToolResult(BaseModel):
    state: Literal["result"] = "result"
    step: Optional[int] = None
    toolCallId: str
    toolName: str
    args: Dict[str, Any]
    result: Dict[str, Any]


ToolInvocation = Union[ToolCall, ToolResult]


class ToolInvocationUIPart(BaseModel):
    """A tool invocation part of a message."""

    type: Literal["tool-invocation"] = "tool-invocation"
    toolInvocation: ToolInvocation = Field(
        ...,
        description="The tool invocation",
    )


class Source(BaseModel):
    sourceType: Literal["url"] = "url"
    id: str = Field(..., description="The ID of the source.")
    url: str = Field(..., description="The URL of the source.")
    title: Optional[str] = Field(None, description="The title of the source.")
    providerMetadata: Optional[Dict[str, Any]] = Field(
        ..., description="Additional provider metadata for the source."
    )


class SourceUIPart(BaseModel):
    """A source part of a message."""

    type: Literal["source"] = "source"
    source: Source = Field(..., description="The source.")


class FileUIPart(BaseModel):
    """A file part of a message."""

    type: Literal["file"] = "file"
    mimeType: str
    data: str


class StepStartUIPart(BaseModel):
    """A step boundary part of a message."""

    type: Literal["step-start"] = "step-start"


UIPart = Union[
    TextUIPart,
    ReasoningUIPart,
    ToolInvocationUIPart,
    SourceUIPart,
    FileUIPart,
    StepStartUIPart,
]


class Message(BaseModel):
    id: str = Field(..., description="A unique identifier for the message")
    createdAt: Optional[str] = Field(None, description="The timestamp of the message")
    content: str = Field(
        ..., description="Text content of the message. Use parts when possible."
    )
    role: str = Field(
        ..., description="The role of the message sender: 'user', 'assistant', 'system'.",
    )
    parts: List[UIPart] = Field(
        default_factory=list,
        description="""\
The parts of the message. Use this for rendering the message in the UI.

Assistant messages can have text, reasoning, and tool invocation parts.
User messages can have text parts.\
""",
    )

class ChatRequest(BaseModel):
    messages: List[Message] = Field(
        default_factory=list,
        description="A list of messages in the chat conversation",
    )


def convert_to_langchain_messages(client_messages: List[Message]):
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
                            "value": '0:"Hello World!"',
                        }
                    }
                }
            },
            "headers": {
                "x-vercel-ai-data-stream": {
                    "description": "Protocol version for Vercel AI streaming",
                    "schema": {"type": "string", "example": "v1"},
                }
            },
        }
    },
)
async def handle_chat_data(request: ChatRequest):
    messages = request.messages

    def generate():
        # Convert ClientMessage objects to LangChain message objects
        langchain_messages = convert_to_langchain_messages(messages)

        chain = model | StrOutputParser()
        for chunk in chain.stream(langchain_messages):
            # Skip empty chunks to avoid JSON parsing issues
            yield "0:{text}\n".format(text=json.dumps(chunk))
        # Use json.dumps with compact formatting (no spaces)
        yield 'd:{"finishReason":"stop"}\n'

    response = StreamingResponse(generate(), media_type="text/plain")
    response.headers["x-vercel-ai-data-stream"] = "v1"
    return response
