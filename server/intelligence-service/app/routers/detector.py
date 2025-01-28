from fastapi import APIRouter
from ..detector.bad_practice_detector import PullRequest, detectbadpractices, BadPracticeList

router = APIRouter(prefix="/detector", tags=["detector"])

@router.post(
    "/",
    response_model=BadPracticeList,
    summary="Detect bad practices given rules.",
)
def detect(request: PullRequest):
    return detectbadpractices(request)
