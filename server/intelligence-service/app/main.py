from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from .model import model
from fastapi.openapi.utils import get_openapi

app = FastAPI(
    title="Intelligence Service API",
    description="API for interacting with the LLM model",
    version="1.0.0",
)

class ChatRequest(BaseModel):
    message: str


@app.post("/chat", response_model=dict, summary="Chat with LLM")
async def chat(request: ChatRequest):
    try:
        response = model.invoke(request.message)
        return { "response": response.content }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

def custom_openapi():
    if app.openapi_schema:
        return app.openapi_schema
    openapi_schema = get_openapi(
        title="Intelligence Service API",
        version="1.0.0",
        description="API for interacting with the LLM model",
        routes=app.routes,
    )
    app.openapi_schema = openapi_schema
    return app.openapi_schema


app.openapi = custom_openapi