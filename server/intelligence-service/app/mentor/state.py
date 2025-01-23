from langgraph.graph.message import add_messages
from typing_extensions import Annotated, TypedDict


class State(TypedDict):
    last_thread: str  # id of the last conversation to integrate long-term memory
    dev_progress: str # a summary of the user's PRs
    messages: Annotated[list, add_messages]
    save_goals: bool # save the goals to the long-term memory
    goal_setting: bool
    development: bool
    status: bool
    impediments: bool
    promises: bool
    update_reflection: bool
    summary: bool
    goal_reflection: bool
    finish: bool  # thank the user and say goodbye
    closed: bool
