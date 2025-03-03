from typing import List

from fastapi import APIRouter
from openai import BaseModel

from ..detector.bad_practice_detector import detect_bad_practices, BadPractice

router = APIRouter(prefix="/detector", tags=["detector"])


class DetectorRequest(BaseModel):
    title: str
    description: str
    bad_practices: List[BadPractice]


class DetectorResponse(BaseModel):
    bad_practices: List[BadPractice]


@router.post(
    "/",
    response_model=DetectorResponse,
    summary="Detect bad practices for given pull request.",
)
def detect(request: DetectorRequest):
    return detect_bad_practices(request.title, request.description, request.bad_practices)
