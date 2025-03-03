from pathlib import Path
from typing import List

from langchain_core.prompts import PromptTemplate, ChatPromptTemplate
from pydantic import BaseModel, Field

from .prompts.pullrequest_badpractice_detector import BAD_PRACTICE_PROMPT_TEST
from ..model import model

class BadPractice(BaseModel):
    """A detected bad practice in a pull request."""

    title: str = Field(description="The title of the bad practice.")
    description: str = Field(description="The description of the bad practice.")
    resolved: bool = Field(description="Whether the bad practice has been resolved.")


class BadPracticeList(BaseModel):
    """A list of bad practices detected in a pull request."""

    bad_practices: List[BadPractice] = Field(
        description="A list of bad practices detected in a pull request."
    )


def detect_bad_practices(title, description, bad_practices) -> BadPracticeList:
    prompt_text = BAD_PRACTICE_PROMPT_TEST
    prompt_template = ChatPromptTemplate.from_template(prompt_text)
    prompt = prompt_template.invoke({"title": title, "description": description, "bad_practices": bad_practices})
    structured_llm = model.with_structured_output(BadPracticeList)
    response = structured_llm.invoke(prompt)
    return response
