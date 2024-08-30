from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from .model import model

app = FastAPI()


class ChatRequest(BaseModel):
    message: str


@app.post("/chat", response_model=dict, summary="Chat with LLM")
async def chat(request: ChatRequest):
    try:
        response = model.invoke(request.message)
        return { "response": response.content }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
