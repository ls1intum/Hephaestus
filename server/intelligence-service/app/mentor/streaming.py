import asyncio
import inspect
import uuid
import json
from typing import AsyncGenerator, Callable, Optional, Any, Dict

from app.mentor.models import (
    StreamErrorPart,
    StreamFinishPart,
    StreamPart,
    StreamReasoningStartPart,
    StreamReasoningDeltaPart,
    StreamReasoningEndPart,
    StreamStepFinishPart,
    StreamStepStartPart,
    StreamTextStartPart,
    StreamTextDeltaPart,
    StreamTextEndPart,
    StreamToolInputAvailablePart,
    StreamToolInputDeltaPart,
    StreamToolInputStartPart,
    StreamToolOutputAvailablePart,
    StreamToolOutputErrorPart,
    StreamStartPart,
    StreamMessageMetadataPart,
    StreamSourceUrlPart,
    StreamSourceDocumentPart,
    StreamFilePart,
    StreamDataPart,
)


class StreamGenerator:
    """Helper class to manage streaming of message parts."""

    def __init__(self, message_id: str = None):
        self.message_id = message_id or str(uuid.uuid4())

    def format_event(self, part: StreamPart) -> str:
        """Convert a StreamPart to a text/event-stream format."""
        return f"data: {json.dumps(part.model_dump(exclude_none=True))}\n\n"

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

    def text_start(self, text_id: str) -> str:
        """Start text content with ID."""
        return self.format_event(StreamTextStartPart(id=text_id))

    def text_delta(self, text_id: str, delta: str) -> str:
        """Send text content delta."""
        return self.format_event(StreamTextDeltaPart(id=text_id, delta=delta))

    def text_end(self, text_id: str) -> str:
        """End text content."""
        return self.format_event(StreamTextEndPart(id=text_id))

    def reasoning_start(self, reasoning_id: str, metadata: Optional[Dict[str, Any]] = None) -> str:
        """Start reasoning content with ID."""
        return self.format_event(
            StreamReasoningStartPart(id=reasoning_id, providerMetadata=metadata)
        )

    def reasoning_delta(self, reasoning_id: str, delta: str, metadata: Optional[Dict[str, Any]] = None) -> str:
        """Send reasoning content delta."""
        return self.format_event(
            StreamReasoningDeltaPart(id=reasoning_id, delta=delta, providerMetadata=metadata)
        )

    def reasoning_end(self, reasoning_id: str, metadata: Optional[Dict[str, Any]] = None) -> str:
        """End reasoning content."""
        return self.format_event(
            StreamReasoningEndPart(id=reasoning_id, providerMetadata=metadata)
        )

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
        provider_executed: Optional[bool] = None,
    ) -> str:
        """Signal that tool output is available."""
        return self.format_event(
            StreamToolOutputAvailablePart(
                toolCallId=tool_call_id,
                output=output_data,
                providerExecuted=provider_executed,
            )
        )

    def tool_output_error(
        self,
        tool_call_id: str,
        error_text: str,
        provider_executed: Optional[bool] = None,
    ) -> str:
        """Signal that tool output has an error."""
        return self.format_event(
            StreamToolOutputErrorPart(
                toolCallId=tool_call_id,
                errorText=error_text,
                providerExecuted=provider_executed,
            )
        )

    def source_url(
        self,
        source_id: str,
        url: str,
        title: Optional[str] = None,
        provider_metadata: Optional[Dict[str, Any]] = None,
    ) -> str:
        """Signal a URL source."""
        return self.format_event(
            StreamSourceUrlPart(
                sourceId=source_id,
                url=url,
                title=title,
                providerMetadata=provider_metadata,
            )
        )

    def source_document(
        self,
        source_id: str,
        media_type: str,
        title: str,
        filename: Optional[str] = None,
        provider_metadata: Optional[Dict[str, Any]] = None,
    ) -> str:
        """Signal a document source."""
        return self.format_event(
            StreamSourceDocumentPart(
                sourceId=source_id,
                mediaType=media_type,
                title=title,
                filename=filename,
                providerMetadata=provider_metadata,
            )
        )

    def file(
        self,
        url: str,
        media_type: str,
    ) -> str:
        """Signal a file attachment."""
        return self.format_event(
            StreamFilePart(
                url=url,
                mediaType=media_type,
            )
        )

    def data(
        self,
        data_type: str,
        data_content: Any,
        data_id: Optional[str] = None,
    ) -> str:
        """Signal custom data."""
        return self.format_event(
            StreamDataPart(
                type=f"data-{data_type}",
                data=data_content,
                id=data_id,
            )
        )

    def message_metadata(
        self,
        metadata: Dict[str, Any],
    ) -> str:
        """Update message metadata."""
        return self.format_event(
            StreamMessageMetadataPart(
                messageMetadata=metadata,
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
