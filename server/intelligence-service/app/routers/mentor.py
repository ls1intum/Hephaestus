import asyncio
import inspect
import uuid
import json
from typing import Annotated, AsyncGenerator, Callable, List, Literal, Optional, Any, Dict, Union
from pydantic import BaseModel, ConfigDict, Field, RootModel
from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from langchain_core.messages import HumanMessage, AIMessage, SystemMessage, AIMessageChunk, ToolMessage
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
    type: Annotated[str, Field(pattern=r"^tool-.*")]  # Will be in format 'tool-{NAME}'
    toolCallId: str


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
    type: Annotated[str, Field(pattern=r"^data-.*")]  # Will be in format 'data-{NAME}'
    id: Optional[str] = None
    data: Any


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
    type: Annotated[str, Field(pattern=r"^data-.*")]  # Will be in format 'data-{NAME}'
    id: Optional[str] = None
    data: Any


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


StreamPartUnion = Union[
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
        tool_calls = []
        tool_call_id = None
        
        for part in msg.parts:
            if isinstance(part, TextUIPart):
                content += part.text
            elif isinstance(part, (ToolInputAvailablePart, ToolOutputAvailablePart)):
                # Handle tool call parts for assistant messages
                if msg.role == "assistant" and isinstance(part, ToolInputAvailablePart):
                    tool_calls.append({
                        "id": part.toolCallId,
                        "name": getattr(part, 'toolName', 'unknown'),
                        "args": part.input
                    })
                # Handle tool results for tool messages  
                elif isinstance(part, ToolOutputAvailablePart):
                    tool_call_id = part.toolCallId
                    content = json.dumps(part.output) if part.output else ""
        
        if msg.role == "user":
            langchain_messages.append(HumanMessage(content=content))
        elif msg.role == "assistant":
            if tool_calls:
                langchain_messages.append(AIMessage(content=content, tool_calls=tool_calls))
            else:
                langchain_messages.append(AIMessage(content=content))
        elif msg.role == "system":
            langchain_messages.append(SystemMessage(content=content))
        elif tool_call_id:  # Tool message
            langchain_messages.append(ToolMessage(content=content, tool_call_id=tool_call_id))

    return langchain_messages


class StreamGenerator:
    """Helper class to manage streaming of message parts."""
    
    def __init__(self, message_id: str = None):
        self.message_id = message_id or str(uuid.uuid4())
    
    def format_event(self, part: StreamPartUnion) -> str:
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


@tool
def get_weather(latitude: float, longitude: float) -> dict:
    """Get the current weather at a specific location.
    
    Args:
        latitude: The latitude coordinate of the location
        longitude: The longitude coordinate of the location
        
    Returns:
        A dictionary containing current weather information including temperature,
        humidity, wind speed, and weather conditions.
    """
    try:
        url = f"https://api.open-meteo.com/v1/forecast?latitude={latitude}&longitude={longitude}&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,rain,showers,snowfall,weather_code,cloud_cover,pressure_msl,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m&hourly=temperature_2m,relative_humidity_2m,precipitation_probability&daily=sunrise,sunset,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
        
        response = requests.get(url, timeout=10)
        response.raise_for_status()
        data = response.json()
        
        # Extract and format the most relevant information
        current = data.get('current', {})
        daily = data.get('daily', {})
        
        formatted_weather = {
            "location": {
                "latitude": latitude,
                "longitude": longitude,
                "timezone": data.get('timezone', 'Unknown')
            },
            "current": {
                "temperature": current.get('temperature_2m'),
                "temperature_unit": data.get('current_units', {}).get('temperature_2m', '°C'),
                "feels_like": current.get('apparent_temperature'),
                "humidity": current.get('relative_humidity_2m'),
                "wind_speed": current.get('wind_speed_10m'),
                "wind_direction": current.get('wind_direction_10m'),
                "pressure": current.get('pressure_msl'),
                "cloud_cover": current.get('cloud_cover'),
                "precipitation": current.get('precipitation', 0),
                "weather_code": current.get('weather_code'),
                "is_day": current.get('is_day', 1) == 1
            },
            "daily": {
                "sunrise": daily.get('sunrise', [None])[0],
                "sunset": daily.get('sunset', [None])[0],
                "temperature_max": daily.get('temperature_2m_max', [None])[0],
                "temperature_min": daily.get('temperature_2m_min', [None])[0]
            },
            "timestamp": current.get('time')
        }
        
        return formatted_weather
        
    except requests.RequestException as e:
        logger.error(f"Error fetching weather data: {e}")
        return {"error": f"Failed to fetch weather data: {str(e)}"}
    except Exception as e:
        logger.error(f"Unexpected error in get_weather: {e}")
        return {"error": f"Unexpected error: {str(e)}"}


system_prompt = """\
You are a friendly assistant! Keep your responses concise and helpful.

About the origin of user's request:
- lat: 48.137154
- lon: 11.576124
- city: Munich
- country: Germany

When you need to call tools, always explain to the user what you're about to do before making the tool call. For example, before calling the weather tool, say something like "Let me check the current weather for your location in Munich."

After receiving tool results, provide a clear and helpful interpretation of the data to the user.
"""


async def generate_response(messages, stream: StreamGenerator):
    """Generate a response using the language model with proper tool calling loop."""
    # Available tools
    tools = [get_weather]
    model_with_tools = model.bind_tools(tools)
    
    # Create tool mapping for execution
    tool_map = {tool.name: tool for tool in tools}
    
    # Prepare messages with system prompt
    working_messages = [SystemMessage(content=system_prompt)] + messages
    
    try:
        # Main conversation loop - handles multiple tool calling rounds
        max_iterations = 5  # Prevent infinite loops
        iteration = 0
        
        while iteration < max_iterations:
            iteration += 1
            logger.debug(f"Starting conversation iteration {iteration}")
            
            # Stream the model response
            gathered_response = AIMessageChunk(content="")
            tool_calls_to_execute = []
            
            logger.info(f"Sending {len(working_messages)} messages to model")
            for i, msg in enumerate(working_messages):
                logger.debug(f"Message {i}: {type(msg).__name__} - {msg.content[:100]}...")
            
            async for chunk in model_with_tools.astream(working_messages):
                chunk = AIMessageChunk.model_validate(chunk)
                
                # Handle tool calls
                if chunk.tool_calls:
                    # On first tool call chunk, start streaming tool input
                    if not gathered_response.tool_calls:
                        logger.info(f"Starting tool calls: {[tc['name'] for tc in chunk.tool_calls]}")
                        for tool_call in chunk.tool_calls:
                            yield stream.tool_input_start(
                                tool_call_id=tool_call["id"],
                                tool_name=tool_call["name"]
                            )
                    
                    # Stream tool input deltas more intelligently
                    for i, tool_call in enumerate(chunk.tool_calls):
                        if i < len(gathered_response.tool_calls):
                            # Continuing existing tool call - stream argument updates
                            existing_args = gathered_response.tool_calls[i].get("args", {})
                            new_args = tool_call.get("args", {})
                            
                            # Only stream meaningful deltas
                            if new_args and new_args != existing_args:
                                # Convert to string for delta streaming
                                try:
                                    delta_str = json.dumps(new_args, separators=(',', ':'))
                                    yield stream.tool_input_delta(
                                        tool_call_id=tool_call["id"],
                                        input_text_delta=delta_str
                                    )
                                except (TypeError, ValueError) as e:
                                    logger.warning(f"Could not serialize tool args for streaming: {e}")
                        else:
                            # New tool call
                            logger.info(f"New tool call detected: {tool_call['name']}")
                            yield stream.tool_input_start(
                                tool_call_id=tool_call["id"],
                                tool_name=tool_call["name"]
                            )
                
                # Handle regular content
                if chunk.content:
                    yield stream.text(chunk.content)
                
                # Accumulate the response
                gathered_response += chunk
            
            # Process completed tool calls
            if gathered_response.tool_calls:
                logger.info(f"Processing {len(gathered_response.tool_calls)} tool calls")
                
                # CRITICAL: Add the assistant's tool-calling message FIRST
                working_messages.append(gathered_response)
                
                for tool_call in gathered_response.tool_calls:
                    tool_name = tool_call["name"]
                    tool_call_id = tool_call["id"]
                    tool_args = tool_call["args"]
                    
                    # Signal tool input is available
                    yield stream.tool_input_available(
                        tool_call_id=tool_call_id,
                        tool_name=tool_name,
                        input_data=tool_args
                    )
                    
                    try:
                        # Execute the tool
                        if tool_name in tool_map:
                            logger.info(f"Executing tool: {tool_name} with args: {tool_args}")
                            
                            # Execute tool function
                            tool_func = tool_map[tool_name]
                            if inspect.iscoroutinefunction(tool_func.func):
                                tool_result = await tool_func.func(**tool_args)
                            else:
                                tool_result = tool_func.func(**tool_args)
                            
                            logger.info(f"Tool {tool_name} result: {tool_result}")
                            
                            # Stream tool output
                            yield stream.tool_output_available(
                                tool_call_id=tool_call_id,
                                output_data=tool_result
                            )
                            
                            # Add tool message to conversation AFTER the AIMessage
                            tool_message = ToolMessage(
                                content=json.dumps(tool_result),
                                tool_call_id=tool_call_id
                            )
                            working_messages.append(tool_message)
                            
                        else:
                            error_msg = f"Unknown tool: {tool_name}"
                            logger.error(error_msg)
                            yield stream.error(error_msg)
                            
                            # Add error tool message
                            tool_message = ToolMessage(
                                content=f"Error: {error_msg}",
                                tool_call_id=tool_call_id
                            )
                            working_messages.append(tool_message)
                            
                    except Exception as e:
                        error_msg = f"Error executing tool {tool_name}: {str(e)}"
                        logger.error(error_msg)
                        yield stream.error(error_msg)
                        
                        # Add error tool message
                        tool_message = ToolMessage(
                            content=f"Error: {error_msg}",
                            tool_call_id=tool_call_id
                        )
                        working_messages.append(tool_message)
                
                # Continue the loop to let the model respond to tool results
                logger.info("Tool execution complete, continuing conversation...")
                continue
            
            else:
                # No tool calls, conversation is complete
                logger.info("No tool calls in response, conversation complete")
                
                # Add final assistant message to working messages
                if gathered_response.content:
                    working_messages.append(gathered_response)
                break
                
    except Exception as e:
        logger.error(f"Error in model response generation: {str(e)}")
        yield stream.error(f"Error generating response: {str(e)}")


class StreamPart(RootModel[StreamPartUnion]):
    """Stream part model."""
    model_config = ConfigDict(title="StreamPart")


@router.post(
    "/chat",
    response_class=StreamingResponse,
    response_model=StreamPart,
    responses={
         200: {
            "description": "Event stream of chat updates.",
            "content": {
                "text/event-stream": {
                    "schema": {"$ref": "#/components/schemas/StreamPart"},
                    "examples": {
                        "text":   {"value": {"type": "text",   "text": "Hello"}},
                        "error":  {"value": {"type": "error",  "errorText": "oops"}}
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
