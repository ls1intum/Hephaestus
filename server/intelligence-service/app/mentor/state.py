from langgraph.graph.message import add_messages
from typing_extensions import Annotated, TypedDict

class State(TypedDict):
    messages: Annotated[list, add_messages]
    status: bool
    impediments: bool
    promises: bool
    summary: bool

