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
            MessagesPlaceholder("messages"),
        ]
    )
    chain = prompt | model
    return {
        "messages": [chain.invoke({"messages": state["messages"]})],
        "status": True,
    }


def ask_status(state: State, store: BaseStore):
    previous_session_id = state["last_thread"]
    print("[ask_status]: previous_session ", previous_session_id)
    if state["last_thread"] == "":
        previous_promises = ""
    else:
        namespace = (previous_session_id, "summary")
        previous_promises = store.search(namespace)
        print("[ask_status]: previous_promises ", previous_promises)
        if not previous_promises:
            previous_promises = ""
        else:
            print("[ask_status]: store ", previous_promises)
            for item in previous_promises:
                if "promises" in item.value:
                    previous_promises = item.value["promises"]
                    break
            # TODO: delete print
            print("[ask_status]: previous_promises: ", previous_promises)

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
    response = chain.invoke({"messages": state["messages"]})
    return {"messages": [response]}


def ask_impediments(state: State, store: BaseStore):
    previous_session_id = state["last_thread"]
    previous_impediments = ""
    if state["last_thread"] != "":
        namespace = (previous_session_id, "summary")
        previous_impediments = store.search(namespace)
        if previous_impediments:
            for item in previous_impediments:
                if "impediments" in item.value:
                    previous_impediments = item.value["impediments"]
                    break
            # TODO: delete print
            print("[ask_impediments]: previous_impediments: ", previous_impediments)

    prompt = ChatPromptTemplate(
        [
            ("system", persona_prompt),
            (
                "system",
                (
                    prompt_loader.get_prompt(
                        type="mentor", name="impediments"
                    ).format_map({"previous_impediments": previous_impediments})
                ),
            ),
            MessagesPlaceholder("messages"),
        ]
    )
    chain = prompt | model
    print(
        "[impediments] prompt: "
        + prompt_loader.get_prompt(type="mentor", name="impediments").format_map(
            {"previous_impediments": previous_impediments}
        )
    )

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
    print("[promises]")
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
    print(
        "[ask_summary]: prompt: ",
        prompt_loader.get_prompt(type="mentor", name="summary"),
    )
    print("[ask_summary]: response from the LLM: ", response.content)
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
        "finish": True,
        "closed": True,
    }


# node responsible for checking the state of the conversation and updating it accordingly
def check_state(state: State):
    step_order = ["status", "impediments", "promises", "summary", "finish"]
    step = next((key for key in step_order if state.get(key)), None)
    if not step:
        return  # exit early if no step is active without state update

    print("[check_state]: current step: " + step)  # TODO: remove prints

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
    test = chain.invoke({"messages": state["messages"]})
    responce = test.content

    print("[check_state]: responce from the LLM: ", responce)

    if responce == "YES":
        step_index = step_order.index(step)
        if step_index < len(step_order) - 1:
            next_step = step_order[step_index + 1]
            print("[check_state]: state changed ")
            print("[check_state]: new current step: " + next_step)
            return {step: False, next_step: True}
        else:
            # if on the last step, mark as closed
            return {"finish": False, "closed": True}
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

        print("[update_memory]: step: ", step)
        chain = prompt | model
        response = chain.invoke({"messages": state["messages"]}).content
        print("[update_memory]: response from the LLM: ", response)
        store.put(namespace, key=str(uuid4()), value={step: response})

    return
