from .state import State


def start_router(state: State):
    if len(state["messages"]) == 0:
        return "greeting"
    return "check_state"


def main_router(state: State):
    if state["goal_setting"]:
        return "check_goals"
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
        return "check_goal_reflection"
    else:  # state["finish"]
        return "finish_node"

def goal_setting_router(state: State):
    # check_goals updated the state and finished the goal setting
    if not state["goal_setting"]:
        return "set_goals"
    return "goal_setting_node"

def goal_reflection_router(state: State):
    # check_goal_reflection updated the state and finished the goal reflection
    if not state["adjust_goals"]:
        return "adjust_goals"
    return "goal_reflection_node"
