import asyncio
from collections import defaultdict
import inspect
import uuid
import json
from typing import AsyncGenerator, Callable, List, Literal, Optional, Any, Dict, Union
from pydantic import BaseModel, Field, field_validator
from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from langchain_core.messages import HumanMessage, AIMessage, SystemMessage, AIMessageChunk, Tool
from langchain_core.tools import tool
import requests
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
    
    def tool_input_start(self, tool_call_id: str, tool_name: str) -> str:
        """Signal the start of tool input."""
        return self.format_event(StreamToolInputStartPart(toolCallId=tool_call_id, toolName=tool_name))
    
    def tool_input_delta(self, tool_call_id: str, input_text_delta: str) -> str:
        """Send a delta of tool input."""
        return self.format_event(StreamToolInputDeltaPart(toolCallId=tool_call_id, inputTextDelta=input_text_delta))
    
    def tool_input_available(self, tool_call_id: str, tool_name: str, input_data: Any) -> str:
        """Signal that tool input is available."""
        return self.format_event(StreamToolInputAvailablePart(toolCallId=tool_call_id, toolName=tool_name, input=input_data))
    
    def tool_output_available(self, tool_call_id: str, output_data: Any, provider_metadata: Optional[Dict[str, Any]] = None) -> str:
        """Signal that tool output is available."""
        return self.format_event(StreamToolOutputAvailablePart(toolCallId=tool_call_id, output=output_data, providerMetadata=provider_metadata))
    
    def done(self) -> str:
        """Signal the end of the stream."""
        return 'data: [DONE]\n\n'
    
    async def run_step(
        self,
        generator_func: Callable[..., Any],
        *args,
        **kwargs
    ) -> AsyncGenerator[str, None]:
        """Wrap any sync- or async-generator in a start/finish boundary."""
        yield self.start_step()
        try:
            kwargs['stream'] = self  # Pass the stream to the generator
            gen = generator_func(*args, **kwargs)
            # If it's an async-generator…
            if inspect.isasyncgen(gen):
                async for chunk in gen:
                    yield chunk
            # If it's a plain generator…
            else:
                for chunk in gen:
                    yield chunk
                    # give the loop a breath
                    await asyncio.sleep(0)
        except Exception as e:
            yield self.error(f"Error in step: {e}")
        yield self.finish_step()


def fetch_weather_data(latitude: float, longitude: float) -> dict:
    """Fetch weather data from the API"""
    try:
        url = f"https://api.open-meteo.com/v1/forecast?latitude={latitude}&longitude={longitude}&current=temperature_2m&hourly=temperature_2m&daily=sunrise,sunset&timezone=auto"
        result = requests.get(url, timeout=10)
        result.raise_for_status()  # Raise an exception for bad status codes
        return result.json()
    except requests.exceptions.RequestException as e:
        logger.error(f"Failed to fetch weather data for lat={latitude}, lon={longitude}: {str(e)}")
        raise Exception(f"Weather API request failed: {str(e)}")
    except json.JSONDecodeError as e:
        logger.error(f"Failed to parse weather API response: {str(e)}")
        raise Exception(f"Weather API response parsing failed: {str(e)}")


@tool
def get_weather(latitude: float, longitude: float) -> dict:
    """Get the current weather at a location"""
    return fetch_weather_data(latitude, longitude)


system_prompt = """\
You are a friendly assistant! Keep your responses concise and helpful.

About the origin of user's request:
- lat: 48.137154
- lon: 11.576124
- city: Munich
- country: Germany

Before calling the weather give the user a brief explanation of what you are doing.
"""


async def generate_response(messages, stream: StreamGenerator):
    """Generate a response using the language model."""
    model_with_tools = model.bind_tools([get_weather])
    messages = [SystemMessage(content=system_prompt)] + messages
    chain = model_with_tools
    
    try:        
        toolCallStarted = {}  # Track which tool calls have been started: {tool_call_id: True}
        toolCallByIndex = {}  # Track tool calls by index: {index: tool_call_id}
        toolCallAccumulatedArgs = {}  # Track accumulated args: {tool_call_id: str}
        toolCallNames = {}  # Track tool names: {tool_call_id: tool_name}
        
        async for chunk in chain.astream(messages):
            logger.debug(f"Received chunk:\n{json.dumps(chunk.model_dump(), indent=2)}")
            chunk = AIMessageChunk.model_validate(chunk)
            
            # Handle tool calls (initial tool call with ID and name)
            if chunk.tool_calls:
                for toolCall in chunk.tool_calls:
                    toolCallId = toolCall["id"]
                    if toolCallId and toolCallId not in toolCallStarted:
                        # This is the start of a new tool call
                        toolCallStarted[toolCallId] = True
                        toolCallAccumulatedArgs[toolCallId] = ""
                        toolCallNames[toolCallId] = toolCall["name"]
                        yield stream.tool_input_start(
                            tool_call_id=toolCallId,
                            tool_name=toolCall["name"]
                        )
            
            # Handle tool call chunks (streaming arguments)
            if chunk.tool_call_chunks:
                for toolChunk in chunk.tool_call_chunks:
                    index = toolChunk.get("index")
                    toolCallId = toolChunk.get("id")
                    
                    # If this chunk has an ID, associate it with the index
                    if toolCallId and index is not None:
                        toolCallByIndex[index] = toolCallId
                    
                    # Get the tool call ID either from this chunk or from the index mapping
                    currentToolCallId = toolCallId or toolCallByIndex.get(index)
                    
                    if currentToolCallId and currentToolCallId in toolCallStarted:
                        # Send the args as delta text
                        argsText = toolChunk.get("args", "")
                        if argsText:
                            # Accumulate args for final tool_input_available
                            toolCallAccumulatedArgs[currentToolCallId] += argsText
                            
                            yield stream.tool_input_delta(
                                tool_call_id=currentToolCallId,
                                input_text_delta=argsText
                            )
            
            # Handle regular text content
            if chunk.content:
                yield stream.text(chunk.content)
                
            # Check if this is the finish chunk (has finish_reason)
            if hasattr(chunk, 'response_metadata') and chunk.response_metadata.get('finish_reason') == 'tool_calls':
                # Send tool_input_available and execute tools for all accumulated tool calls
                for toolCallId, accumulatedArgs in toolCallAccumulatedArgs.items():
                    if accumulatedArgs and toolCallId in toolCallStarted:
                        try:
                            # Parse the accumulated JSON arguments
                            parsedArgs = json.loads(accumulatedArgs) if accumulatedArgs else {}
                            # Get tool name from stored names
                            toolName = toolCallNames.get(toolCallId, "unknown")
                            
                            # Send tool_input_available
                            yield stream.tool_input_available(
                                tool_call_id=toolCallId,
                                tool_name=toolName,
                                input_data=parsedArgs
                            )
                            
                            # Execute the tool and send tool_output_available
                            try:
                                if toolName == "get_weather":
                                    # Execute the weather tool
                                    latitude = parsedArgs.get("latitude")
                                    longitude = parsedArgs.get("longitude")
                                    if latitude is not None and longitude is not None:
                                        logger.info(f"Executing weather tool with lat={latitude}, lon={longitude}")
                                        # Call the weather function directly
                                        toolOutput = fetch_weather_data(latitude, longitude)
                                        yield stream.tool_output_available(
                                            tool_call_id=toolCallId,
                                            output_data=toolOutput
                                        )
                                    else:
                                        logger.error(f"Missing required arguments for weather tool: {parsedArgs}")
                                        yield stream.tool_output_available(
                                            tool_call_id=toolCallId,
                                            output_data={"error": "Missing required arguments: latitude and longitude"}
                                        )
                                else:
                                    logger.warning(f"Unknown tool: {toolName}")
                                    yield stream.tool_output_available(
                                        tool_call_id=toolCallId,
                                        output_data={"error": f"Unknown tool: {toolName}"}
                                    )
                            except Exception as toolError:
                                logger.error(f"Error executing tool {toolName}: {str(toolError)}")
                                yield stream.tool_output_available(
                                    tool_call_id=toolCallId,
                                    output_data={"error": f"Tool execution failed: {str(toolError)}"}
                                )
                                
                        except json.JSONDecodeError as e:
                            logger.error(f"Failed to parse tool arguments for {toolCallId}: {accumulatedArgs}")
                            # Still send the raw args if JSON parsing fails
                            toolName = toolCallNames.get(toolCallId, "unknown")
                            yield stream.tool_input_available(
                                tool_call_id=toolCallId,
                                tool_name=toolName,
                                input_data=accumulatedArgs
                            )
                            yield stream.tool_output_available(
                                tool_call_id=toolCallId,
                                output_data={"error": f"Invalid JSON arguments: {str(e)}"}
                            )
                
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
            
            async for chunk in stream.run_step(generate_response, langchain_messages):
                yield chunk

            yield stream.finish_message()
        except Exception as e:
            logger.error(f"Error generating response: {str(e)}")
            yield stream.error(f"An error occurred: {str(e)}")
        
        yield stream.done()

    return StreamingResponse(generate(), media_type="text/event-stream")
