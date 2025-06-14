from app.mentor.state import State
from app.models import get_model
from app.settings import settings
from app.mentor.prompt_loader import PromptLoader
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from uuid import uuid4
from langchain_core.runnables.config import RunnableConfig
from langgraph.store.base import BaseStore


prompt_loader = PromptLoader()
ChatModel = get_model(settings.MODEL_NAME)
model = ChatModel(temperature=0.7, max_tokens=4096)


# updating the long-term session memory with the sprint progress: impediments and promises
def update_memory(state: State, config: RunnableConfig, *, store: BaseStore):
    session_id = config["configurable"]["thread_id"]
    namespace = (session_id, "summary")
    steps = ["impediments", "promises"]  # steps to process

    for step in steps:
        prompt = ChatPromptTemplate(
            [
                (
                    "system",
                    prompt_loader.get_prompt(
                        type="analyzer", name="update_memory"
                    ).format_map({"step": step}),
                ),
                MessagesPlaceholder("messages"),
            ]
        )

        chain = prompt | model
        response = chain.invoke({"messages": state["messages"]}).content
        store.put(namespace, key=str(uuid4()), value={step: response})

    return


def set_goals(state: State, *, store: BaseStore):
    user_id = state["user_id"]
    namespace = (user_id, "goals")

    prompt = ChatPromptTemplate(
        [
            (
                "system",
                prompt_loader.get_prompt(type="analyzer", name="set_goals"),
            ),
            MessagesPlaceholder("messages"),
        ]
    )

    chain = prompt | model
    response = chain.invoke({"messages": state["messages"]}).content
    store.put(namespace, key=str(uuid4()), value={"goal_list": response})
    return


def adjust_goals(state: State, *, store: BaseStore):
    user_id = state["user_id"]
    namespace = (user_id, "goals")
    goals = store.search(namespace)
    if not goals:
        goals = ""
    else:
        for item in goals:
            if "goal_list" in item.value:
                goals = item.value["goal_list"]
                break

    prompt = ChatPromptTemplate(
        [
            (
                "system",
                prompt_loader.get_prompt(
                    type="analyzer", name="adjust_goals"
                ).format_map({"goals": goals}),
            ),
            MessagesPlaceholder("messages"),
        ]
    )

    chain = prompt | model
    response = chain.invoke({"messages": state["messages"]}).content
    store.put(namespace, key=str(uuid4()), value={"goal_list": response})
    return
