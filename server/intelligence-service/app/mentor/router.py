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
    # Log basic payload summary for traceability
    try:
        first_role = request.messages[0].role if request.messages else None
        last_role = request.messages[-1].role if request.messages else None
        first_text = (
            next(
                (
                    p.text
                    for p in (request.messages[0].parts or [])
                    if getattr(p, "type", None) == "text" and getattr(p, "text", None)
                ),
                None,
            )
            if request.messages
            else None
        )
        last_text = (
            next(
                (
                    p.text
                    for p in (request.messages[-1].parts or [])
                    if getattr(p, "type", None) == "text" and getattr(p, "text", None)
                ),
                None,
            )
            if request.messages
            else None
        )
        logger.info(
            "Processing chat request: user_id=%s, messages=%s, firstRole=%s, lastRole=%s, firstText=%s, lastText=%s",
            request.user_id,
            len(request.messages or []),
            first_role,
            last_role,
            (
                (first_text[:80] + "…")
                if first_text and len(first_text) > 80
                else first_text
            ),
            (last_text[:80] + "…") if last_text and len(last_text) > 80 else last_text,
        )
    except Exception:
        logger.warning("Failed to summarize chat request for logging")

    async def generate():
        try:
            yield stream.start_message()

            langchain_messages = convert_to_langchain_messages(request.messages)

            async for chunk in stream.run_step(
                generate_response, langchain_messages, request.user_id
            ):
                yield chunk

            yield stream.finish_message()
        except Exception as e:
            # Log full exception info with traceback for diagnostics
            logger.exception("Error generating response")
            yield stream.error(f"An error occurred: {str(e)}")

        yield stream.done()

    return StreamingResponse(generate(), media_type="text/event-stream")
