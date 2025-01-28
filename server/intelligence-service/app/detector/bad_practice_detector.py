from pathlib import Path
from typing import List

from langchain_core.prompts import PromptTemplate, ChatPromptTemplate
from pydantic import BaseModel, Field

from ..model import model


class PullRequest(BaseModel):
    id: str
    title: str
    description: str

class BadPractice(BaseModel):
    """A detected bad practice in a pull request."""

    title: str = Field(description="The title of the bad practice.")
    description: str = Field(description="The description of the bad practice.")

class BadPracticeList(BaseModel):
    """A list of bad practices detected in a pull request."""

    bad_practices: List[BadPractice] = Field(description="A list of bad practices detected in a pull request.")


def detectbadpractices(title, description) -> BadPracticeList:
   prompt_path = Path(__file__).parent / "prompts" / "pullrequest_badpractice_detector.txt"
   with open(prompt_path, "r", encoding="utf-8") as f:
       prompt_text = f.read()
   prompt_template = ChatPromptTemplate.from_template(prompt_text)
   prompt = prompt_template.invoke({"title": title, "description": description})
   structured_llm = model.with_structured_output(BadPracticeList)
   response = structured_llm.invoke(prompt)
   return response