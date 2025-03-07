from langchain_core.prompts import ChatPromptTemplate
from langchain_core.runnables.config import RunnableConfig

from app.settings import settings
from app.models import get_model
from app.analyzer.dimensions import ReviewQualityAssessment
from app.analyzer.prompts.review_quality_prompt import REVIEW_QUALITY_PROMPT

ChatModel = get_model(settings.ANALYZER_MODEL_NAME)
model = ChatModel(temperature=1)


def analyze_review_quality(diff_hunk: str, review_comment: str, config: RunnableConfig = None) -> ReviewQualityAssessment:
    """
    Analyzes the quality of a code review comment for a given diff hunk.

    Args:
        diff_hunk: The code diff that the review comment refers to
        review_comment: The code review comment to analyze
        config: Optional configuration for the LLM

    Returns:
        A detailed assessment of the code review quality
    """
    prompt = ChatPromptTemplate.from_template(REVIEW_QUALITY_PROMPT)
    structured_llm = model.with_structured_output(ReviewQualityAssessment)
    runnable = prompt | structured_llm
    response = runnable.invoke({ "diff_hunk": diff_hunk, "review_comment": review_comment }, config)
    return response