from langgraph.graph.message import add_messages
from typing_extensions import Annotated, TypedDict


class State(TypedDict):
    last_thread: str  # id of the last conversation to integrate long-term memory
    user_id: str
    dev_progress: str  # a summary of the user's PRs
    messages: Annotated[list, add_messages]
    goal_setting: bool
    development: bool
    status: bool
    impediments: bool
    promises: bool
    summary: bool
    goal_reflection: bool
    finish: bool  # thank the user for the participation
    closed: bool
