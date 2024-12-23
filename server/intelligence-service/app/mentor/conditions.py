from .state import State

def start_router(state: State):
    if len(state["messages"]) == 1:  # TODO: change this to 0
        return "greeting"
    return "check_state"

def router(state: State):
    print("WE ARE IN ROUTER!\nrouter state: ", state["status"], state["impediments"], state["promises"], state["summary"])

    if state["status"]:
        return "status_node"
    elif state["impediments"]:
        return "impediments_node"
    elif state["promises"]:
        return "promises_node"
    elif state["summary"]:
        return "summary_node"
    return "mentor_node"