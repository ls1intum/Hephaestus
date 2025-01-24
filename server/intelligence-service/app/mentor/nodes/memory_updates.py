from .state import State
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from ..model import model
from uuid import uuid4
from langchain_core.runnables.config import RunnableConfig
from langgraph.store.base import BaseStore
from .prompt_loader import PromptLoader

prompt_loader = PromptLoader()


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


def save_goals(state: State, config: RunnableConfig, *, store: BaseStore):
    user_id = state["user_id"]
    namespace = (user_id, "goals")
    prompt = ChatPromptTemplate(
        [
            (
                "system",
                prompt_loader.get_prompt(type="analyzer", name="update_memory"),
            ),
            MessagesPlaceholder("messages"),
        ]
    )

    chain = prompt | model
    response = chain.invoke({"messages": state["messages"]}).content
    store.put(namespace, key=str(uuid4()), value={step: response})

    return

def adjust_goals(state: State, config: RunnableConfig, *, store: BaseStore):
    user_id = state["user_id"]
    # find a goal -> re-write it 
    namespace = (user_id, "goals")
    prompt = ChatPromptTemplate(
        [
            (
                "system",
                prompt_loader.get_prompt(type="analyzer", name="update_memory"),
            ),
            MessagesPlaceholder("messages"),
        ]
    )

    chain = prompt | model
    response = chain.invoke({"messages": state["messages"]}).content
    store.put(namespace, key=str(uuid4()), value={step: response})

    return