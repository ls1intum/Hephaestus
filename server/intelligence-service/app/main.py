from fastapi import FastAPI
from pydantic import BaseModel
from .model import send_message

app = FastAPI(
    title="Hephaestus Intelligence Service API",
    description="API documentation for the Hephaestus Intelligence Service.",
    version="0.0.1",
    contact={"name": "Felix T.J. Dietrich", "email": "felixtj.dietrich@tum.de"},
)


class ChatRequest(BaseModel):
    session_id: str
    message_content: str


class ChatResponse(BaseModel):
    session_id: str
    message_content: str


@app.post(
    "/chat",
    response_model=ChatResponse,
    summary="Start and continue a chat session with an LLM.",
)
async def chat(request: ChatRequest):
    state = {"messages": []}

    result = send_message(
        session_id=request.session_id, input_message=request.message_content, state=state
    )

    response_message = result["response"]["messages"][-1].content
    return ChatResponse(session_id=request.session_id, message_content=response_message)
