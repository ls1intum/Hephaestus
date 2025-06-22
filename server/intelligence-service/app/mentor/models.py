from typing import Annotated, List, Literal, Optional, Any, Dict, Union
from pydantic import BaseModel, Field


class BasePart(BaseModel):
    """Base class for UIMessage and Stream parts."""

    type: str


# --- UI Message Parts ---
# UIMessage parts are part of the ChatRequest structure


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

    type: Annotated[str, Field(pattern=r"^tool-.*")]  # Will be in format 'tool-{NAME}'
    toolCallId: str


class ToolInputStreamingPart(ToolPartBase):
    """Tool part with input being streamed."""

    state: Literal["input-streaming"] = "input-streaming"
    input: Optional[Dict[str, Any]] = None


class ToolInputAvailablePart(ToolPartBase):
    """Tool part with input available."""

    state: Literal["input-available"] = "input-available"
    input: Dict[str, Any]


class ToolOutputAvailablePart(ToolPartBase):
    """Tool part with output available."""

    state: Literal["output-available"] = "output-available"
    input: Dict[str, Any]
    output: Dict[str, Any]


class DataUIPart(BasePart):
    """A data part with dynamic type."""

    type: Annotated[str, Field(pattern=r"^data-.*")]  # Will be in format 'data-{NAME}'
    id: Optional[str] = None
    data: Dict[str, Any]


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
    StepStartUIPart,
]


class UIMessage(BaseModel):
    """Message model that matches the TypeScript interface."""

    id: str = Field(..., description="A unique identifier for the message")
    role: Literal["system", "user", "assistant"]
    metadata: Optional[Dict[str, Any]] = None
    parts: List[UIMessagePart]


# --- Stream Response Parts ---
# Stream response parts are used in the streaming response of the chat endpoint


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
    input: Dict[str, Any]


class StreamToolOutputAvailablePart(BasePart):
    """Tool output available event."""

    type: Literal["tool-output-available"] = "tool-output-available"
    toolCallId: str
    output: Dict[str, Any]
    providerMetadata: Optional[Dict[str, Any]] = None


class StreamToolOutputErrorPart(BasePart):
    """Tool output error event."""

    type: Literal["tool-output-error"] = "tool-output-error"
    toolCallId: str
    errorText: str


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

    type: Annotated[str, Field(pattern=r"^data-.*")]  # Will be in format 'data-{NAME}'
    id: Optional[str] = None
    data: Dict[str, Any]


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
    StreamToolOutputErrorPart,
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
