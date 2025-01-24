from .state import State
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from ..model import model
from uuid import uuid4
from langchain_core.runnables.config import RunnableConfig
from langgraph.store.base import BaseStore
from .prompt_loader import PromptLoader

prompt_loader = PromptLoader()
persona_prompt = prompt_loader.get_prompt(type="mentor", name="persona")


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
        "development": True,  # directly update the state to the next step
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
    resp = chain.invoke({"messages": state["messages"]})

    return {
        "messages": [resp],
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
    response = chain.invoke({"messages": state["messages"]})
    return {"messages": [response]}


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
    }


# node responsible for checking the state of the conversation and updating it accordingly
def check_state(state: State):
    if state["development"]:
        # call dev_progress node only if there is development progress to show
        if state["dev_progress"] == "":
            return {"development": False, "status": True}
        else:
            return

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
    return


# node responsible for updating the long-term session memory, that can be used across multiple sessions
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


# node responsible for generating responses after the user has finished the project update
def talk_to_mentor(state: State):
    prompt = ChatPromptTemplate(
        [
            ("system", persona_prompt),
            MessagesPlaceholder("messages"),
        ]
    )
    chain = prompt | model
    return {"messages": [chain.invoke({"messages": state["messages"]})]}
