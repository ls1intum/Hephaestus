from fastapi import FastAPI, HTTPException
from .auth.router import router
from .config import settings
from .services.langchain_client import get_openai_client
from pydantic import BaseModel

app = FastAPI(title=settings.APP_NAME)

app.include_router(router)


class ChatRequest(BaseModel):
    message: str


@app.post("/chat", response_model=dict, summary="Chat with LLM")
async def chat(request: ChatRequest):
    try:
        client = get_openai_client()
        response = client.invoke(request.message)
        return {"response": response}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
