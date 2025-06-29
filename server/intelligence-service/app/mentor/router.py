from typing import List, Optional, Any, Dict
from pydantic import BaseModel, ConfigDict, RootModel
from fastapi import APIRouter
from fastapi.responses import StreamingResponse

from app.logger import logger
from app.mentor.response import generate_response
from app.mentor.converter import convert_to_langchain_messages
from app.mentor.models import StreamPart, UIMessage
from app.mentor.streaming import StreamGenerator


router = APIRouter(prefix="/mentor", tags=["mentor"])


class ChatRequest(BaseModel):
    """Chat request model."""

    id: Optional[str] = None
    messages: List[UIMessage]
    metadata: Optional[Dict[str, Any]] = None
    user_id: int


class ChatResponse(RootModel[StreamPart]):
    """Response model for chat streaming."""

    model_config = ConfigDict(title="StreamPart")


@router.post(
    "/chat",
    response_class=StreamingResponse,
    response_model=ChatResponse,
    responses={
        200: {
            "description": "Event stream of chat updates.",
            "content": {
                "text/event-stream": {
                    "schema": {"$ref": "#/components/schemas/ChatResponse"},
                    "examples": {
                        "text": {"value": {"type": "text", "text": "Hello"}},
                        "error": {"value": {"type": "error", "errorText": "oops"}},
                    },
                }
            },
        }
    },
)
async def handle_chat(request: ChatRequest):
    stream = StreamGenerator()
    logger.info(f"Processing chat request with message ID: {stream.message_id}, user_id: {request.user_id}")

    async def generate():
        try:
            yield stream.start_message()

            langchain_messages = convert_to_langchain_messages(request.messages)

            async for chunk in stream.run_step(generate_response, langchain_messages, request.user_id):
                yield chunk

            yield stream.finish_message()
        except Exception as e:
            logger.error(f"Error generating response: {str(e)}")
            yield stream.error(f"An error occurred: {str(e)}")

        yield stream.done()

    return StreamingResponse(generate(), media_type="text/event-stream")
