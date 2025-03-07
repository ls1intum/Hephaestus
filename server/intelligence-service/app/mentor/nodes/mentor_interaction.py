from ..state import State
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from app.models import get_model
from langgraph.store.base import BaseStore
from ..prompt_loader import PromptLoader
from app.settings import settings

prompt_loader = PromptLoader()
persona_prompt = prompt_loader.get_prompt(type="mentor", name="persona")

ChatModel = get_model(settings.MODEL_NAME)
model = ChatModel(temperature=0.7, max_tokens=4096)


def greet(state: State):
    prompt = ChatPromptTemplate(
        [
            ("system", persona_prompt),
            ("system", prompt_loader.get_prompt(type="mentor", name="greeting")),
        ]
    )
    chain = prompt | model

    return {
        "messages": [chain.invoke({"messages": state["messages"]})],
        "goal_setting": True,  # directly update the state to the next possible step
    }


def get_dev_progress(state: State):
    progress = state["dev_progress"]
    prompt = ChatPromptTemplate(
        [
            ("system", persona_prompt),
            (
                "system",
                prompt_loader.get_prompt(type="mentor", name="dev_progress").format_map(
                    {"progress": progress}
                ),
            ),
        ]
    )
    chain = prompt | model
    return {
        "messages": [chain.invoke({"messages": state["messages"]})],
        "development": False,
        "status": True,
    }


def ask_status(state: State, store: BaseStore):
    previous_session_id = state["last_thread"]
    if state["last_thread"] == "":
        previous_promises = ""
    else:
        namespace = (previous_session_id, "summary")
        previous_promises = store.search(namespace)
        if not previous_promises:
            previous_promises = ""
        else:
            for item in previous_promises:
                if "promises" in item.value:
                    previous_promises = item.value["promises"]
                    break

    prompt = ChatPromptTemplate(
        [
            ("system", persona_prompt),
            (
                "system",
                (
                    prompt_loader.get_prompt(type="mentor", name="status").format_map(
                        {"previous_promises": previous_promises}
                    )
                ),
            ),
            MessagesPlaceholder("messages"),
        ]
    )

    chain = prompt | model
    return {"messages": [chain.invoke({"messages": state["messages"]})]}


def ask_impediments(state: State, store: BaseStore):
    print("I am in ask_impediments")
    previous_session_id = state["last_thread"]
    progress = state["dev_progress"]
    previous_impediments = ""
    if state["last_thread"] != "":
        namespace = (previous_session_id, "summary")
        previous_impediments = store.search(namespace)
        if previous_impediments:
            for item in previous_impediments:
                if "impediments" in item.value:
                    previous_impediments = item.value["impediments"]
                    break

    prompt = ChatPromptTemplate(
        [
            ("system", persona_prompt),
            (
                "system",
                (
                    prompt_loader.get_prompt(
                        type="mentor", name="impediments"
                    ).format_map(
                        {
                            "previous_impediments": previous_impediments,
                            "dev_progress": progress,
                        }
                    )
                ),
            ),
            MessagesPlaceholder("messages"),
        ]
    )
    chain = prompt | model
    return {"messages": [chain.invoke({"messages": state["messages"]})]}


def ask_promises(state: State):
    prompt = ChatPromptTemplate(
        [
            ("system", persona_prompt),
            ("system", prompt_loader.get_prompt(type="mentor", name="promises")),
            MessagesPlaceholder("messages"),
        ]
    )
    chain = prompt | model
    return {"messages": [chain.invoke({"messages": state["messages"]})]}


def ask_summary(state: State):
    prompt = ChatPromptTemplate(
        [
            ("system", persona_prompt),
            ("system", prompt_loader.get_prompt(type="mentor", name="summary")),
            MessagesPlaceholder("messages"),
        ]
    )
    chain = prompt | model
    return {"messages": [chain.invoke({"messages": state["messages"]})]}


def finish(state: State):
    prompt = ChatPromptTemplate(
        [
            ("system", persona_prompt),
            ("system", prompt_loader.get_prompt(type="mentor", name="finish")),
            MessagesPlaceholder("messages"),
        ]
    )
    chain = prompt | model
    return {
        "messages": [chain.invoke({"messages": state["messages"]})],
        "finish": False,
        "closed": True,
        "mentor_node": True,
    }


def ask_goals(state: State):
    prompt = ChatPromptTemplate(
        [
            ("system", persona_prompt),
            ("system", prompt_loader.get_prompt(type="mentor", name="goal_setting")),
            MessagesPlaceholder("messages"),
        ]
    )
    chain = prompt | model
    return {"messages": [chain.invoke({"messages": state["messages"]})]}


def reflect_goals(state: State, store: BaseStore):
    print("I am in reflect_goals")
    user_id = state["user_id"]
    namespace = (user_id, "goals")
    goals = store.search(namespace)
    if not goals:
        goals = ""
    else:
        for item in goals:
            if "goal_list" in item.value:
                goals = item.value["goal_list"]

    prompt = ChatPromptTemplate(
        [
            ("system", persona_prompt),
            (
                "system",
                prompt_loader.get_prompt(
                    type="mentor", name="goal_reflection"
                ).format_map({"goals": goals}),
            ),
            MessagesPlaceholder("messages"),
        ]
    )
    chain = prompt | model
    return {"messages": [chain.invoke({"messages": state["messages"]})]}
