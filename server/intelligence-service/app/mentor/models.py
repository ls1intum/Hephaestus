from __future__ import annotations
from typing import Annotated, List, Literal, Optional, Any, Dict, Union
from datetime import datetime
from pydantic import BaseModel, Field, ConfigDict, field_validator
import logging

logger = logging.getLogger(__name__)


class BasePart(BaseModel):
    """Base class for UIMessage and Stream parts."""

    model_config = ConfigDict(extra="ignore", exclude_none=True)

    type: str


# --- UI Message Parts ---
# UIMessage parts are part of the ChatRequest structure


class TextUIPart(BasePart):
    """A text part of a message."""

    type: Literal["text"] = "text"
    text: str
    state: Optional[Literal["streaming", "done"]] = None


class ReasoningUIPart(BasePart):
    """A reasoning part of a message."""

    type: Literal["reasoning"] = "reasoning"
    text: str
    state: Optional[Literal["streaming", "done"]] = None
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

    state: Literal["input-streaming"]
    input: Optional[Dict[str, Any]] = None
    providerExecuted: Optional[bool] = None


class ToolInputAvailablePart(ToolPartBase):
    """Tool part with input available."""

    state: Literal["input-available"]
    input: Dict[str, Any]
    providerExecuted: Optional[bool] = None


class ToolOutputAvailablePart(ToolPartBase):
    """Tool part with output available."""

    state: Literal["output-available"]
    input: Dict[str, Any]
    output: Dict[str, Any]
    providerExecuted: Optional[bool] = None


class ToolOutputErrorPart(ToolPartBase):
    """Tool part with output error."""

    state: Literal["output-error"]
    input: Dict[str, Any]
    errorText: str
    providerExecuted: Optional[bool] = None


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
    ToolOutputErrorPart,
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

    @field_validator("parts", mode="before")
    @classmethod
    def validate_parts(cls, v):
        """Custom validator to handle Java serialization format more flexibly."""
        if not isinstance(v, list):
            return v

        logger.debug(f"🔍 Validating {len(v)} message parts")
        validated_parts = []

        for i, part in enumerate(v):
            if not isinstance(part, dict):
                logger.debug(f"Part {i}: Non-dict part, keeping as-is")
                validated_parts.append(part)
                continue

            # Extract only the necessary fields based on type
            part_type = part.get("type")
            logger.debug(
                f"Part {i}: Processing type='{part_type}' with keys: {list(part.keys())}"
            )

            if part_type == "text":
                # Create a clean TextUIPart with only the required fields
                clean_part = {
                    "type": "text",
                    "text": part.get("text", ""),  # Required field
                }
                # Only add state if it's valid
                if "state" in part and part["state"] in ["streaming", "done"]:
                    clean_part["state"] = part["state"]
                    logger.debug(f"Part {i}: Added valid state: {part['state']}")
                elif "state" in part:
                    logger.debug(f"Part {i}: Skipped invalid state: {part['state']}")
                validated_parts.append(clean_part)
                logger.debug(f"Part {i}: ✅ Created TextUIPart: {clean_part}")

            elif part_type == "reasoning":
                clean_part = {
                    "type": "reasoning",
                    "text": part.get("text", ""),  # Required field
                }
                # Only add state if it's valid
                if "state" in part and part["state"] in ["streaming", "done"]:
                    clean_part["state"] = part["state"]
                if "providerMetadata" in part and part["providerMetadata"] is not None:
                    clean_part["providerMetadata"] = part["providerMetadata"]
                validated_parts.append(clean_part)
                logger.debug(f"Part {i}: ✅ Created ReasoningUIPart: {clean_part}")

            elif part_type and part_type.startswith("tool-") and part.get("toolCallId"):
                # ✅ Only process tool parts if they have valid toolCallId and proper type pattern
                tool_call_id = part.get("toolCallId")
                if not tool_call_id or tool_call_id == "null":
                    logger.debug(
                        f"Part {i}: ❌ Skipped tool part - invalid toolCallId: {tool_call_id}"
                    )
                    continue

                clean_part = {
                    "type": part_type,
                    "toolCallId": tool_call_id,
                }

                state = part.get("state")
                if state == "input-streaming":
                    clean_part["state"] = "input-streaming"
                    if "input" in part and part["input"] is not None:
                        clean_part["input"] = part["input"]
                elif state == "input-available":
                    clean_part["state"] = "input-available"
                    clean_part["input"] = part.get(
                        "input", {}
                    )  # Required for this state
                elif state == "output-available":
                    clean_part["state"] = "output-available"
                    clean_part["input"] = part.get("input", {})  # Required
                    clean_part["output"] = part.get("output", {})  # Required
                elif state == "output-error":
                    clean_part["state"] = "output-error"
                    clean_part["input"] = part.get("input", {})  # Required
                    clean_part["errorText"] = part.get("errorText", "")  # Required
                else:
                    logger.debug(
                        f"Part {i}: ❌ Skipped tool part - invalid state: {state}"
                    )
                    continue

                if "providerExecuted" in part and part["providerExecuted"] is not None:
                    clean_part["providerExecuted"] = part["providerExecuted"]
                validated_parts.append(clean_part)
                logger.debug(f"Part {i}: ✅ Created tool part: {clean_part}")

            elif part_type == "source-url":
                source_id = part.get("sourceId")
                url = part.get("url")
                if not source_id or not url:
                    logger.debug(
                        f"Part {i}: ❌ Skipped source-url - missing required fields"
                    )
                    continue
                clean_part = {
                    "type": "source-url",
                    "sourceId": source_id,  # Required
                    "url": url,  # Required
                }
                if "title" in part and part["title"] is not None:
                    clean_part["title"] = part["title"]
                if "providerMetadata" in part and part["providerMetadata"] is not None:
                    clean_part["providerMetadata"] = part["providerMetadata"]
                validated_parts.append(clean_part)
                logger.debug(f"Part {i}: ✅ Created SourceUrlUIPart: {clean_part}")

            elif part_type == "source-document":
                source_id = part.get("sourceId")
                media_type = part.get("mediaType")
                title = part.get("title")
                if not source_id or not media_type or not title:
                    logger.debug(
                        f"Part {i}: ❌ Skipped source-document - missing required fields"
                    )
                    continue
                clean_part = {
                    "type": "source-document",
                    "sourceId": source_id,  # Required
                    "mediaType": media_type,  # Required
                    "title": title,  # Required
                }
                if "filename" in part and part["filename"] is not None:
                    clean_part["filename"] = part["filename"]
                if "providerMetadata" in part and part["providerMetadata"] is not None:
                    clean_part["providerMetadata"] = part["providerMetadata"]
                validated_parts.append(clean_part)
                logger.debug(f"Part {i}: ✅ Created SourceDocumentUIPart: {clean_part}")

            elif part_type == "file":
                media_type = part.get("mediaType")
                url = part.get("url")
                if not media_type or not url:
                    logger.debug(f"Part {i}: ❌ Skipped file - missing required fields")
                    continue
                clean_part = {
                    "type": "file",
                    "mediaType": media_type,  # Required
                    "url": url,  # Required
                }
                if "filename" in part and part["filename"] is not None:
                    clean_part["filename"] = part["filename"]
                validated_parts.append(clean_part)
                logger.debug(f"Part {i}: ✅ Created FileUIPart: {clean_part}")

            elif part_type and part_type.startswith("data-"):
                data = part.get("data")
                if not isinstance(data, dict):
                    logger.debug(f"Part {i}: ❌ Skipped data part - invalid data field")
                    continue
                clean_part = {
                    "type": part_type,
                    "data": data,  # Required and must be dict
                }
                if "id" in part and part["id"] is not None:
                    clean_part["id"] = part["id"]
                validated_parts.append(clean_part)
                logger.debug(f"Part {i}: ✅ Created DataUIPart: {clean_part}")

            elif part_type == "step-start":
                clean_part = {"type": "step-start"}
                validated_parts.append(clean_part)
                logger.debug(f"Part {i}: ✅ Created StepStartUIPart: {clean_part}")

            else:
                # For unrecognized types, only keep if it's a basic valid structure
                if part_type and isinstance(part.get("type"), str):
                    # Keep the original part but strip out obviously invalid fields
                    clean_part = {"type": part_type}
                    # Only copy fields that aren't null/None
                    for key, value in part.items():
                        if key != "type" and value is not None and value != "null":
                            clean_part[key] = value
                    validated_parts.append(clean_part)
                    logger.debug(f"Part {i}: ⚠️ Kept unrecognized part: {clean_part}")
                else:
                    logger.debug(f"Part {i}: ❌ Completely invalid part, skipping")

        logger.debug(
            f"🎯 Validation complete: {len(validated_parts)}/{len(v)} parts validated successfully"
        )
        return validated_parts


# --- Stream Response Parts ---
# Stream response parts are used in the streaming response of the chat endpoint


class StreamTextStartPart(BasePart):
    """Text stream start part."""

    type: Literal["text-start"] = "text-start"
    id: str


class StreamTextDeltaPart(BasePart):
    """Text stream delta part."""

    type: Literal["text-delta"] = "text-delta"
    id: str
    delta: str


class StreamTextEndPart(BasePart):
    """Text stream end part."""

    type: Literal["text-end"] = "text-end"
    id: str


class StreamReasoningStartPart(BasePart):
    """Reasoning stream start part."""

    type: Literal["reasoning-start"] = "reasoning-start"
    id: str
    providerMetadata: Optional[Dict[str, Any]] = None


class StreamReasoningDeltaPart(BasePart):
    """Reasoning stream delta part."""

    type: Literal["reasoning-delta"] = "reasoning-delta"
    id: str
    delta: str
    providerMetadata: Optional[Dict[str, Any]] = None


class StreamReasoningEndPart(BasePart):
    """Reasoning stream end part."""

    type: Literal["reasoning-end"] = "reasoning-end"
    id: str
    providerMetadata: Optional[Dict[str, Any]] = None


class StreamErrorPart(BasePart):
    """Error stream part."""

    type: Literal["error"] = "error"
    errorText: str


class StreamToolInputStartPart(BasePart):
    """Tool input start event."""

    type: Literal["tool-input-start"] = "tool-input-start"
    toolCallId: str
    toolName: str
    providerExecuted: Optional[bool] = None


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
    providerExecuted: Optional[bool] = None


class StreamToolOutputAvailablePart(BasePart):
    """Tool output available event."""

    type: Literal["tool-output-available"] = "tool-output-available"
    toolCallId: str
    output: Dict[str, Any]
    providerExecuted: Optional[bool] = None


class StreamToolOutputErrorPart(BasePart):
    """Tool output error event."""

    type: Literal["tool-output-error"] = "tool-output-error"
    toolCallId: str
    errorText: str
    providerExecuted: Optional[bool] = None


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
    StreamTextStartPart,
    StreamTextDeltaPart,
    StreamTextEndPart,
    StreamReasoningStartPart,
    StreamReasoningDeltaPart,
    StreamReasoningEndPart,
    StreamErrorPart,
    StreamToolInputStartPart,
    StreamToolInputDeltaPart,
    StreamToolInputAvailablePart,
    StreamToolOutputAvailablePart,
    StreamToolOutputErrorPart,
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

# --- Tool-specific input/output models for OpenAPI typing ---


class ToolInputBase(BaseModel):
    """Base class for all tool input models. Used for auto-discovery in OpenAPI generation."""
    pass


class ToolOutputBase(BaseModel):
    """Base class for all tool output models. Used for auto-discovery in OpenAPI generation."""
    pass


class CreateDocumentInput(ToolInputBase):
    """Input for createDocument tool."""

    title: str
    content: str
    kind: Literal["text"]


class UpdateDocumentInput(ToolInputBase):
    """Input for updateDocument tool."""

    id: str  # UUID string
    title: str
    content: str
    kind: Literal["text"]


class BaseDocumentOutput(ToolOutputBase):
    """Base output payload returned by document tools."""

    id: str
    createdAt: datetime
    title: str
    content: str
    kind: Literal["TEXT"]
    userId: str


class CreateDocumentOutput(BaseDocumentOutput):
    """Output for createDocument tool."""


class UpdateDocumentOutput(BaseDocumentOutput):
    """Output for updateDocument tool."""


class GetWeatherInput(ToolInputBase):
    """Input for getWeather tool."""

    latitude: float
    longitude: float


class WeatherCurrentUnits(BaseModel):
    time: Optional[str] = None
    interval: Optional[str] = None
    temperature_2m: Optional[str] = None


class WeatherCurrent(BaseModel):
    time: Optional[str] = None
    interval: Optional[int] = None
    temperature_2m: Optional[float] = None


class WeatherHourlyUnits(BaseModel):
    time: Optional[str] = None
    temperature_2m: Optional[str] = None


class WeatherHourly(BaseModel):
    time: List[str] = []
    temperature_2m: List[float] = []


class WeatherDailyUnits(BaseModel):
    time: Optional[str] = None
    sunrise: Optional[str] = None
    sunset: Optional[str] = None


class WeatherDaily(BaseModel):
    time: List[str] = []
    sunrise: List[str] = []
    sunset: List[str] = []


class GetWeatherOutput(ToolOutputBase):
    """Output for getWeather tool, aligned with WeatherTool.tsx expectations."""

    latitude: Optional[float] = None
    longitude: Optional[float] = None
    generationtime_ms: Optional[float] = None
    utc_offset_seconds: Optional[int] = None
    timezone: Optional[str] = None
    timezone_abbreviation: Optional[str] = None
    elevation: Optional[float] = None

    current_units: Optional[WeatherCurrentUnits] = None
    current: Optional[WeatherCurrent] = None
    hourly_units: Optional[WeatherHourlyUnits] = None
    hourly: Optional[WeatherHourly] = None
    daily_units: Optional[WeatherDailyUnits] = None
    daily: Optional[WeatherDaily] = None


# Unions used by the tool UI parts
ToolInputUnion = Union[CreateDocumentInput, UpdateDocumentInput, GetWeatherInput]
ToolOutputUnion = Union[CreateDocumentOutput, UpdateDocumentOutput, GetWeatherOutput]

# Resolve forward references for OpenAPI
ToolInputStreamingPart.model_rebuild()
ToolInputAvailablePart.model_rebuild()
ToolOutputAvailablePart.model_rebuild()
ToolOutputErrorPart.model_rebuild()
