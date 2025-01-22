from .state import State
from langgraph.graph import END


def start_router(state: State):
    if len(state["messages"]) == 0:
        return "greeting"
    return "check_state"


def main_router(state: State):
    if state["development"]:
        return "development_node"
    elif state["status"]:
        return "status_node"
    elif state["impediments"]:
        return "impediments_node"
    elif state["promises"]:
        return "promises_node"
    elif state["summary"]:
        return "summary_node"
    else:  # state["finish"]
        return "finish_node"
