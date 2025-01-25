from .state import State
from langgraph.graph import END


def start_router(state: State):
    if len(state["messages"]) == 0:
        return "greeting"
    return "check_state"


def main_router(state: State):
    if state["goal_setting"]:
        return "goal_setting_node"
    elif state["update_reflection"]:
        return "update_reflection_node"
    elif state["goal_setting"]:
        return "goal_setting_node"
    elif state["development"]:
        return "development_node"
    elif state["status"]:
        return "status_node"
    elif state["impediments"]:
        return "impediments_node"
    elif state["promises"]:
        return "promises_node"
    elif state["summary"]:
        return "summary_node"
    elif state["goal_reflection"]:
        return "goal_reflection_node"
    else:  # state["finish"]
        return "finish_node"

def goal_setting_router(state: State):
    if state["save_goals"]:
        return "save_goals"
    return END

def goal_reflection_router(state: State):
    if state["adjust_goals"]:
        return "adjust_goals"
    return END
