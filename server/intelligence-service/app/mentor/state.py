from langgraph.graph.message import add_messages
from typing_extensions import Annotated, TypedDict


class State(TypedDict):
    last_thread: str  # id of the last conversation to integrate long-term memory
    messages: Annotated[list, add_messages]
    status: bool
    impediments: bool
    promises: bool
    summary: bool
    finish: bool  # thank the user and say goodbye
    closed: bool
