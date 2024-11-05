from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from .model import start_chat as start_chat_function, chat as chat_function
from typing import Dict, Optional

app = FastAPI(
    title="Hephaestus Intelligence Service API",
    description="API documentation for the Hephaestus Intelligence Service.",
    version="0.0.1",
    contact={"name": "Felix T.J. Dietrich", "email": "felixtj.dietrich@tum.de"},
)

# Global dictionary to store conversation states
conversations: Dict[str, dict] = {}


class ChatRequest(BaseModel):
    message: str
    thread_id: Optional[str] = None


class ChatResponse(BaseModel):
    response: str
    thread_id: Optional[str] = None


@app.post(
    "/chat",
    response_model=ChatResponse,
    summary="Start and continue a chat session with an LLM.",
)
async def chat(request: ChatRequest):
    if request.thread_id is None:
        # Start a new chat session
        result = start_chat_function(request.message)
        thread_id = result["thread_id"]
        state = result["state"]
        response_message = result["response"]["messages"][-1].content
        conversations[thread_id] = state
        return ChatResponse(thread_id=thread_id, response=response_message)
    else:
        thread_id = request.thread_id
        # Check if the thread_id exists
        if thread_id not in conversations:
            raise HTTPException(status_code=404, detail="Thread ID not found")
        state = conversations[thread_id]
        user_input = request.message
        result = chat_function(thread_id, user_input, state)
        state = result["state"]
        response_message = result["response"]["messages"][-1].content
        conversations[thread_id] = state
        return ChatResponse(response=response_message)
