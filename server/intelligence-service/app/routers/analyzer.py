from fastapi import APIRouter
from pydantic import BaseModel
from langchain_core.runnables.config import RunnableConfig
from langfuse.callback import CallbackHandler

from app.settings import settings
from app.analyzer.review_quality_analyzer import analyze_review_quality, ReviewQualityAssessment

router = APIRouter(prefix="/analyzer", tags=["analyzer"])

callbacks = []
if settings.langfuse_enabled:
    langfuse_handler = CallbackHandler()
    langfuse_handler.auth_check()
    callbacks.append(langfuse_handler)


class ReviewQualityRequest(BaseModel):
    diff_hunk: str
    review_comment: str


@router.post(
    "/review-quality",
    response_model=ReviewQualityAssessment,
    summary="Analyze the quality of a code review comment"
)
def assess_review_quality(request: ReviewQualityRequest):
    """
    Analyzes the quality of a code review comment for a given diff hunk.
    
    Returns a detailed assessment with scores for different dimensions of review quality 
    and suggestions for improvement.
    """
    config = RunnableConfig(callbacks=callbacks, run_name="Review Quality Assessment")
    return analyze_review_quality(request.diff_hunk, request.review_comment, config)