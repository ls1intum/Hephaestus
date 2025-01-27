from typing import List
from fastapi import APIRouter
from pydantic import BaseModel
from ..detector.bad_practice_detector import PullRequestWithBadPractices, PullRequest, Rule, detectbadpractices

router = APIRouter(prefix="/detector", tags=["detector"])

class DetectorRequest(BaseModel):
    pull_requests: List[PullRequest]
    rules: List[Rule]

class DetectorResponse(BaseModel):
    detectBadPractices: List[PullRequestWithBadPractices]

@router.post(
    "/",
    response_model=DetectorResponse,
    summary="Detect bad practices given rules.",
)
def detect(request: DetectorRequest):
    return detectbadpractices(request.pull_requests, request.rules)
