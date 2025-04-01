from enum import Enum
from typing import List

from langchain_core.prompts import ChatPromptTemplate
from pydantic import BaseModel, Field

from app.settings import settings
from app.detector.prompts.pullrequest_badpractice_detector import (
    BAD_PRACTICE_PROMPT_TEST,
)
from app.models import get_model

ChatModel = get_model(settings.DETECTION_MODEL_NAME)
model = ChatModel()


class BadPracticeStatus(str, Enum):
    GOOD_PRACTICE = "Good Practice"
    FIXED = "Fixed"
    CRITICAL_ISSUE = "Critical Issue"
    NORMAL = "Normal Issue"
    MINOR = "Minor Issue"
    WONT_FIX = "Won't Fix"
    WRONG = "Wrong"


class BadPractice(BaseModel):
    """A detected bad practice in a pull request."""

    title: str = Field(description="The title of the bad practice.")
    description: str = Field(description="The description of the bad practice.")
    status: BadPracticeStatus = Field(description="The status of the bad practice.")


class BadPracticeResult(BaseModel):
    """A list of bad practices detected in a pull request."""

    bad_practice_summary: str = Field(
        description="A summary of the bad practices detected in the pull request."
    )
    bad_practices: List[BadPractice] = Field(
        description="A list of bad practices detected in a pull request."
    )


def detect_bad_practices(
    title, description, lifecycle_state, bad_practice_summary, bad_practices
) -> BadPracticeResult:
    prompt_text = BAD_PRACTICE_PROMPT_TEST
    prompt_template = ChatPromptTemplate.from_template(prompt_text)
    prompt = prompt_template.invoke(
        {
            "title": title,
            "description": description,
            "lifecycle_state": lifecycle_state,
            "bad_practice_summary": bad_practice_summary,
            "bad_practices": bad_practices,
        }
    )
    structured_llm = model.with_structured_output(BadPracticeResult)
    response = structured_llm.invoke(prompt)
    return response
