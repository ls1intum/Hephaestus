from app.mentor.state import State
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from app.models import get_model
from app.settings import settings
from app.mentor.prompt_loader import PromptLoader

prompt_loader = PromptLoader()

ChatModel = get_model(settings.MODEL_NAME)
model = ChatModel(temperature=0.7, max_tokens=4096)


def check_state(state: State):
    if state["closed"] or state["finish"] or state["goal_reflection"]:
        # closed (finished) session state does not need to be updated
        # when goal reflection is active, state is updated in the check_goal_reflection node
        return

    if state["goal_setting"] and state["last_thread"] == "":
        # first conversation with mentor, state is updated in the check_goals node
        return

    if state["development"] or state["goal_setting"]:
        # call dev_progress node only if there is development progress to show
        if state["dev_progress"] == "":
            return {"goal_setting": False, "development": False, "status": True}
        else:
            return {"goal_setting": False, "development": True}

    step_order = [
        "status",
        "impediments",
        "promises",
        "summary",
        "goal_reflection",
        "finish",
    ]
    step = next((key for key in step_order if state.get(key)), None)
    if not step:
        return  # exit early if no step is active without state update

    prompt = ChatPromptTemplate(
        [
            (
                "system",
                prompt_loader.get_prompt(
                    type="analyzer", name="check_updates"
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


# decides on whether the goal setting process is finished
def check_goals(state: State):
    prompt = ChatPromptTemplate(
        [
            (
                "system",
                prompt_loader.get_prompt(
                    type="analyzer", name="check_goals"
                ).format_map({"history": state["messages"]}),
            ),
            MessagesPlaceholder("messages"),
        ]
    )

    chain = prompt | model
    response = chain.invoke({"messages": state["messages"]}).content

    if response == "YES":
        return {"goal_setting": False, "development": True}

    return


# decides on whether the goal reflection process is finished
def check_goal_reflection(state: State):
    prompt = ChatPromptTemplate(
        [
            (
                "system",
                prompt_loader.get_prompt(type="analyzer", name="check_goal_reflection"),
            ),
            MessagesPlaceholder("messages"),
        ]
    )

    chain = prompt | model
    response = chain.invoke({"messages": state["messages"]}).content

    if response == "YES":
        return {"goal_reflection": False, "finish": True}

    return
