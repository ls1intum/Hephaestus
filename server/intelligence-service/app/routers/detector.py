from typing import List

from fastapi import APIRouter
from pydantic import BaseModel

from app.detector.bad_practice_detector import detect_bad_practices, BadPractice

router = APIRouter(prefix="/detector", tags=["detector"])


class DetectorRequest(BaseModel):
    title: str
    description: str
    lifecycle_state: str
    repository_name: str
    pull_request_number: int
    bad_practice_summary: str
    bad_practices: List[BadPractice]
    pull_request_template: str


class DetectorResponse(BaseModel):
    bad_practice_summary: str
    bad_practices: List[BadPractice]
    trace_id: str


@router.post(
    "/",
    response_model=DetectorResponse,
    summary="Detect bad practices for given pull request.",
)
def detect(request: DetectorRequest):
    result = detect_bad_practices(
        request.title,
        request.description,
        request.lifecycle_state,
        request.repository_name,
        request.pull_request_number,
        request.bad_practice_summary,
        request.bad_practices,
        request.pull_request_template,
    )
    return DetectorResponse(
        bad_practice_summary=result.bad_practice_summary,
        bad_practices=result.bad_practices,
        trace_id=result.trace_id,
    )
