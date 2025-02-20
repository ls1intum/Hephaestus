from langgraph.graph.message import add_messages
from typing_extensions import Annotated, TypedDict


class State(TypedDict):
    last_thread: str  # id of the last conversation to integrate long-term memory
    dev_progress: str
    messages: Annotated[list, add_messages]
    development: bool
    status: bool
    impediments: bool
    promises: bool
    summary: bool
    finish: bool  # thank the user and say goodbye
    closed: bool
