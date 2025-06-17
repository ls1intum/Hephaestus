import asyncio
import uuid
import json
from typing import AsyncGenerator, List, Literal, Optional, Any, Dict, Union
from pydantic import BaseModel, Field, field_validator
from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from langchain_core.output_parsers import StrOutputParser
from langchain_core.messages import HumanMessage, AIMessage, SystemMessage
from app.models import get_model
from app.settings import settings
from app.logger import logger


router = APIRouter(prefix="/mentor", tags=["mentor"])

ChatModel = get_model(settings.MODEL_NAME)
model = ChatModel(temperature=0.7, max_tokens=4096)


class BasePart(BaseModel):
    """Base class for UIMessage and Stream parts."""
    type: str


# --- UI Message Parts ---

class TextUIPart(BasePart):
    """A text part of a message."""
    type: Literal["text"] = "text"
    text: str


class ReasoningUIPart(BasePart):
    """A reasoning part of a message."""
    type: Literal["reasoning"] = "reasoning"
    text: str
    providerMetadata: Optional[Dict[str, Any]] = None


class SourceUrlUIPart(BasePart):
    """A URL source part of a message."""
    type: Literal["source-url"] = "source-url"
    sourceId: str
    url: str
    title: Optional[str] = None
    providerMetadata: Optional[Dict[str, Any]] = None


class SourceDocumentUIPart(BasePart):
    """A document source part of a message."""
    type: Literal["source-document"] = "source-document"
    sourceId: str
    mediaType: str
    title: str
    filename: Optional[str] = None
    providerMetadata: Optional[Dict[str, Any]] = None


class FileUIPart(BasePart):
    """A file part of a message."""
    type: Literal["file"] = "file"
    mediaType: str
    filename: Optional[str] = None
    url: str


class StepStartUIPart(BasePart):
    """A step boundary part of a message."""
    type: Literal["step-start"] = "step-start"


class ToolPartBase(BasePart):
    """Base class for tool parts."""
    type: str  # Will be in format 'tool-{NAME}'
    toolCallId: str

    @field_validator('type')
    @classmethod
    def validate_tool_type_prefix(cls, v: str) -> str:
        if not v.startswith("tool-"):
            raise ValueError(f"Tool type must start with 'tool-', got {v}")
        return v

class ToolInputStreamingPart(ToolPartBase):
    """Tool part with input being streamed."""
    state: Literal["input-streaming"] = "input-streaming"
    input: Optional[Any] = None


class ToolInputAvailablePart(ToolPartBase):
    """Tool part with input available."""
    state: Literal["input-available"] = "input-available"
    input: Any


class ToolOutputAvailablePart(ToolPartBase):
    """Tool part with output available."""
    state: Literal["output-available"] = "output-available"
    input: Any
    output: Any


class DataUIPart(BasePart):
    """A data part with dynamic type."""
    type: str  # Will be in format 'data-{NAME}'
    id: Optional[str] = None
    data: Any

    @field_validator('type')
    @classmethod
    def validate_data_type_prefix(cls, v: str) -> str:
        if not v.startswith("data-"):
            raise ValueError(f"Data type must start with 'data-', got {v}")
        return v

# Define the union of all possible UI parts
UIMessagePart = Union[
    TextUIPart,
    ReasoningUIPart,
    ToolInputStreamingPart, 
    ToolInputAvailablePart, 
    ToolOutputAvailablePart,
    SourceUrlUIPart,
    SourceDocumentUIPart,
    FileUIPart,
    DataUIPart,
    StepStartUIPart
]


class UIMessage(BaseModel):
    """Message model that matches the TypeScript interface."""
    id: str = Field(..., description="A unique identifier for the message")
    role: Literal["system", "user", "assistant"]
    metadata: Optional[Dict[str, Any]] = None
    parts: List[UIMessagePart]


# Chat request model
class ChatRequest(BaseModel):
    """Chat request model."""
    id: Optional[str] = None
    messages: List[UIMessage]
    metadata: Optional[Dict[str, Any]] = None


# --- Stream Response Parts ---

class StreamTextPart(BasePart):
    """Text stream part."""
    type: Literal["text"] = "text"
    text: str


class StreamErrorPart(BasePart):
    """Error stream part."""
    type: Literal["error"] = "error"
    errorText: str


class StreamToolInputStartPart(BasePart):
    """Tool input start event."""
    type: Literal["tool-input-start"] = "tool-input-start"
    toolCallId: str
    toolName: str


class StreamToolInputDeltaPart(BasePart):
    """Tool input delta event."""
    type: Literal["tool-input-delta"] = "tool-input-delta"
    toolCallId: str
    inputTextDelta: str


class StreamToolInputAvailablePart(BasePart):
    """Tool input available event."""
    type: Literal["tool-input-available"] = "tool-input-available"
    toolCallId: str
    toolName: str
    input: Any


class StreamToolOutputAvailablePart(BasePart):
    """Tool output available event."""
    type: Literal["tool-output-available"] = "tool-output-available"
    toolCallId: str
    output: Any
    providerMetadata: Optional[Dict[str, Any]] = None


class StreamReasoningPart(BasePart):
    """Reasoning part of a message."""
    type: Literal["reasoning"] = "reasoning"
    text: str
    providerMetadata: Optional[Dict[str, Any]] = None


class StreamReasoningFinishPart(BasePart):
    """Reasoning finish event."""
    type: Literal["reasoning-part-finish"] = "reasoning-part-finish"


class StreamSourceUrlPart(BasePart):
    """Source URL part of a message."""
    type: Literal["source-url"] = "source-url"
    sourceId: str
    url: str
    title: Optional[str] = None
    providerMetadata: Optional[Dict[str, Any]] = None


class StreamSourceDocumentPart(BasePart):
    """Source document part of a message."""
    type: Literal["source-document"] = "source-document"
    sourceId: str
    mediaType: str
    title: str
    filename: Optional[str] = None
    providerMetadata: Optional[Dict[str, Any]] = None


class StreamFilePart(BasePart):
    """File part of a message."""
    type: Literal["file"] = "file"
    url: str
    mediaType: str


class StreamDataPart(BasePart):
    """Data part with dynamic type."""
    type: str  # Will be in format 'data-{NAME}'
    id: Optional[str] = None
    data: Any

    @field_validator('type')
    @classmethod
    def validate_data_type_prefix(cls, v: str) -> str:
        if not v.startswith("data-"):
            raise ValueError(f"Data type must start with 'data-', got {v}")
        return v


class StreamStepStartPart(BasePart):
    """Step start event."""
    type: Literal["start-step"] = "start-step"

    

class StreamStepFinishPart(BasePart):
    """Step finish event."""
    type: Literal["finish-step"] = "finish-step"


class StreamStartPart(BasePart):
    """Start of stream event."""
    type: Literal["start"] = "start"
    messageId: Optional[str] = None
    messageMetadata: Optional[Dict[str, Any]] = None


class StreamFinishPart(BasePart):
    """End of stream event."""
    type: Literal["finish"] = "finish"
    messageMetadata: Optional[Dict[str, Any]] = None


class StreamMessageMetadataPart(BasePart):
    """Message metadata part."""
    type: Literal["message-metadata"] = "message-metadata"
    messageMetadata: Dict[str, Any]


StreamPart = Union[
    StreamTextPart,
    StreamErrorPart,
    StreamToolInputStartPart,
    StreamToolInputDeltaPart,
    StreamToolInputAvailablePart,
    StreamToolOutputAvailablePart,
    StreamReasoningPart,
    StreamReasoningFinishPart,
    StreamSourceUrlPart,
    StreamSourceDocumentPart,
    StreamFilePart,
    StreamDataPart,
    StreamStepStartPart,
    StreamStepFinishPart,
    StreamStartPart,
    StreamFinishPart,
    StreamMessageMetadataPart,
]


def convert_to_langchain_messages(messages: List[UIMessage]):
    """Convert UIMessage objects to LangChain message objects."""
    langchain_messages = []

    for msg in messages:
        
        # Extract text content from parts
        content = ""
        for part in msg.parts:
            if isinstance(part, TextUIPart):
                content += part.text
        # TODO Tool call and other parts handling
        
        if msg.role == "user":
            langchain_messages.append(HumanMessage(content=content))
        elif msg.role == "assistant":
            langchain_messages.append(AIMessage(content=content))
        elif msg.role == "system":
            langchain_messages.append(SystemMessage(content=content))

    return langchain_messages


class StreamGenerator:
    """Helper class to manage streaming of message parts."""
    
    def __init__(self, message_id: str = None):
        self.message_id = message_id or str(uuid.uuid4())
    
    def format_event(self, part: StreamPart) -> str:
        """Convert a StreamPart to a text/event-stream format."""
        return f'data: {json.dumps(part.model_dump())}\n\n'
    
    def start_message(self, metadata: Optional[Dict[str, Any]] = None) -> str:
        """Start a message stream."""
        return self.format_event(StreamStartPart(messageId=self.message_id, messageMetadata=metadata))
    
    def finish_message(self, metadata: Optional[Dict[str, Any]] = None) -> str:
        """Finish a message stream."""
        return self.format_event(StreamFinishPart(messageMetadata=metadata))
    
    def start_step(self) -> str:
        """Start a step in the stream."""
        return self.format_event(StreamStepStartPart())
    
    def finish_step(self) -> str:
        """Finish a step in the stream."""
        return self.format_event(StreamStepFinishPart())
    
    def text(self, text: str) -> str:
        """Send text content."""
        return self.format_event(StreamTextPart(text=text))
    
    def reasoning(self, text: str, metadata: Optional[Dict[str, Any]] = None) -> str:
        """Send reasoning content."""
        return self.format_event(StreamReasoningPart(text=text, providerMetadata=metadata))
    
    def reasoning_finish(self) -> str:
        """Finish reasoning section."""
        return self.format_event(StreamReasoningFinishPart())
    
    def error(self, error_text: str) -> str:
        """Send an error."""
        return self.format_event(StreamErrorPart(errorText=error_text))
    
    def done(self) -> str:
        """Signal the end of the stream."""
        return 'data: [DONE]\n\n'
    
    async def run_step(self, generator_func) -> AsyncGenerator[str, None]:
        """Run a generator function as a step."""
        yield self.start_step()
        
        try:
            if asyncio.iscoroutinefunction(generator_func):
                async for chunk in generator_func():
                    yield chunk
            else:
                # Handle synchronous generators
                for chunk in generator_func():
                    yield chunk
                    # Allow other tasks to run
                    await asyncio.sleep(0)
        except Exception as e:
            logger.error(f"Error in step generation: {str(e)}")
            yield self.error(f"Error in step: {str(e)}")
            
        yield self.finish_step()


def generate_response(messages, stream: StreamGenerator):
    """Generate a response using the language model."""
    chain = model | StrOutputParser()
    
    try:        
        for chunk in chain.stream(messages):
            if chunk:
                yield stream.text(chunk)
    except Exception as e:
        logger.error(f"Error in model response generation: {str(e)}")
        yield stream.error(f"Error generating response: {str(e)}")


@router.post(
    "/chat",
    responses={
        200: {
            "description": "Data stream for AI SDK",
            "content": {
                "text/event-stream": {
                    "examples": {
                        "first-chunk": {
                            "summary": "Typical event stream frame",
                            "value": 'data: {"type":"text","text":"Hello"}\n\n',
                        }
                    }
                }
            },
        }
    },
)
async def handle_chat(request: ChatRequest):    
    stream = StreamGenerator()
    logger.info(f"Processing chat request with message ID: {stream.message_id}")
    
    async def generate():
        try:
            yield stream.start_message()
            
            langchain_messages = convert_to_langchain_messages(request.messages)
            
            async for chunk in stream.run_step(lambda: generate_response(langchain_messages, stream)):
                yield chunk

            yield stream.finish_message(request)
        except Exception as e:
            logger.error(f"Error generating response: {str(e)}")
            yield stream.error(f"An error occurred: {str(e)}")
        
        yield stream.done()

    return StreamingResponse(generate(), media_type="text/event-stream")
