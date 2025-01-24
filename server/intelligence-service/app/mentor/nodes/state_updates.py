from .state import State
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from ..model import model
from uuid import uuid4
from langchain_core.runnables.config import RunnableConfig
from langgraph.store.base import BaseStore
from .prompt_loader import PromptLoader

prompt_loader = PromptLoader()

def check_state(state: State):
    if state["goal_setting"]:
        if state["last_thread"] == "":
            return
    
    if state["development"]:
        # call dev_progress node only if there is development progress to show
        if state["dev_progress"] == "":
            return {"development": False, "status": True}
        else:
            return {"goal_setting": False, "development": True}

    step_order = ["status", "impediments", "promises", "summary", "finish"]
    step = next((key for key in step_order if state.get(key)), None)
    if not step:
        return  # exit early if no step is active without state update

    prompt = ChatPromptTemplate(
        [
            (
                "system",
                prompt_loader.get_prompt(
                    type="analyzer", name="check_state"
                ).format_map({"step": step}),
            ),
            MessagesPlaceholder("messages"),
        ]
    )

    chain = prompt | model

    if chain.invoke({"messages": state["messages"]}).content == "YES":
        step_index = step_order.index(step)
        if step_index < len(step_order) - 1:
            next_step = step_order[step_index + 1]
            return {step: False, next_step: True}
        else:
            # if on the last step, mark as closed
            return {"finish": False, "closed": True}
    return

def check_goals(state: State):
    pass

def adjust_goals(state: State):
    pass