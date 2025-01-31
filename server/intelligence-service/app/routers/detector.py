from typing import List

from fastapi import APIRouter
from openai import BaseModel

from ..detector.bad_practice_detector import PullRequest, detectbadpractices, BadPracticeList, BadPractice

router = APIRouter(prefix="/detector", tags=["detector"])

class DetectorRequest(BaseModel):
    title: str
    description: str

class DetectorResponse(BaseModel):
    bad_practices: List[BadPractice]

@router.post(
    "/",
    response_model=DetectorResponse,
    summary="Detect bad practices for given pull request.",
)
def detect(request: DetectorRequest):
    return detectbadpractices(request.title, request.description)
