import asyncio
import inspect
import uuid
import json
from typing import AsyncGenerator, Callable, Optional, Any, Dict

from app.mentor.models import (
    StreamErrorPart,
    StreamFinishPart,
    StreamPart,
    StreamReasoningFinishPart,
    StreamReasoningPart,
    StreamStartPart,
    StreamStepFinishPart,
    StreamStepStartPart,
    StreamTextPart,
    StreamToolInputAvailablePart,
    StreamToolInputDeltaPart,
    StreamToolInputStartPart,
    StreamToolOutputAvailablePart,
)


class StreamGenerator:
    """Helper class to manage streaming of message parts."""

    def __init__(self, message_id: str = None):
        self.message_id = message_id or str(uuid.uuid4())

    def format_event(self, part: StreamPart) -> str:
        """Convert a StreamPart to a text/event-stream format."""
        return f"data: {json.dumps(part.model_dump())}\n\n"

    def start_message(self, metadata: Optional[Dict[str, Any]] = None) -> str:
        """Start a message stream."""
        return self.format_event(
            StreamStartPart(messageId=self.message_id, messageMetadata=metadata)
        )

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
        return self.format_event(
            StreamReasoningPart(text=text, providerMetadata=metadata)
        )

    def reasoning_finish(self) -> str:
        """Finish reasoning section."""
        return self.format_event(StreamReasoningFinishPart())

    def error(self, error_text: str) -> str:
        """Send an error."""
        return self.format_event(StreamErrorPart(errorText=error_text))

    def tool_input_start(self, tool_call_id: str, tool_name: str) -> str:
        """Signal the start of tool input."""
        return self.format_event(
            StreamToolInputStartPart(toolCallId=tool_call_id, toolName=tool_name)
        )

    def tool_input_delta(self, tool_call_id: str, input_text_delta: str) -> str:
        """Send a delta of tool input."""
        return self.format_event(
            StreamToolInputDeltaPart(
                toolCallId=tool_call_id, inputTextDelta=input_text_delta
            )
        )

    def tool_input_available(
        self, tool_call_id: str, tool_name: str, input_data: Any
    ) -> str:
        """Signal that tool input is available."""
        return self.format_event(
            StreamToolInputAvailablePart(
                toolCallId=tool_call_id, toolName=tool_name, input=input_data
            )
        )

    def tool_output_available(
        self,
        tool_call_id: str,
        output_data: Any,
        provider_metadata: Optional[Dict[str, Any]] = None,
    ) -> str:
        """Signal that tool output is available."""
        return self.format_event(
            StreamToolOutputAvailablePart(
                toolCallId=tool_call_id,
                output=output_data,
                providerMetadata=provider_metadata,
            )
        )

    def done(self) -> str:
        """Signal the end of the stream."""
        return "data: [DONE]\n\n"

    async def run_step(
        self, generator_func: Callable[..., Any], *args, **kwargs
    ) -> AsyncGenerator[str, None]:
        """Wrap any sync- or async-generator in a start/finish boundary."""
        yield self.start_step()
        try:
            # Pass the stream to the generator along with additional args
            gen = generator_func(*args, stream=self, **kwargs)
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
