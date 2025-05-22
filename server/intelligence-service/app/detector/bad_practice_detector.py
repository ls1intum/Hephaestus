from enum import Enum
from typing import List

from langchain_core.prompts import PromptTemplate
from langchain_core.runnables import RunnableConfig
from langfuse import Langfuse
from langfuse.callback import CallbackHandler
from langfuse.decorators import observe, langfuse_context
from pydantic import BaseModel, Field
from app.models import get_model
from app.settings import settings

ChatModel = get_model(settings.DETECTION_MODEL_NAME)
model = ChatModel()
langfuse = Langfuse()


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


class DetectionResult(BaseModel):
    bad_practice_summary: str
    bad_practices: List[BadPractice]
    trace_id: str = ""


@observe()
def detect_bad_practices(
    title,
    description,
    lifecycle_state,
    repository_name,
    pull_request_number,
    bad_practice_summary,
    bad_practices,
    pull_request_template,
) -> DetectionResult:

    callbacks: List[CallbackHandler] = []
    if settings.langfuse_enabled:
        langfuse_handler = langfuse_context.get_current_langchain_handler()
        langfuse_handler.auth_check()
        callbacks.append(langfuse_handler)
    config = RunnableConfig(callbacks=callbacks)
    langfuse_prompt = langfuse.get_prompt("Bad-Practice-Detector")
    langchain_prompt = PromptTemplate.from_template(
        langfuse_prompt.get_langchain_prompt(),
        metadata={"langfuse_prompt": langfuse_prompt},
    )
    structured_llm = model.with_structured_output(BadPracticeResult)
    chain = langchain_prompt | structured_llm
    response = chain.invoke(
        {
            "title": title,
            "description": description,
            "lifecycle_state": lifecycle_state,
            "repository_name": repository_name,
            "bad_practice_summary": bad_practice_summary,
            "bad_practices": bad_practices,
            "pull_request_template": pull_request_template,
        },
        config=config,
    )
    trace_id = langfuse_context.get_current_trace_id() or ""
    session_id = repository_name + "#" + str(pull_request_number)
    langfuse_context.update_current_observation(
        prompt=langfuse_prompt,
    )
    langfuse_context.update_current_trace(
        tags=[repository_name, str(pull_request_number)], session_id=session_id
    )
    return DetectionResult(
        bad_practice_summary=response.bad_practice_summary,
        bad_practices=response.bad_practices,
        trace_id=trace_id,
    )
