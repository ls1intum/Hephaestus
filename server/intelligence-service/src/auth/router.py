from fastapi import APIRouter

router = APIRouter()


@router.get("/", response_model=dict, summary="Hello World Endpoint")
async def hello_world():
    return {"message": "Hello, World!"}
