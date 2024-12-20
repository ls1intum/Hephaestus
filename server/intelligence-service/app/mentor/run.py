from typing_extensions import Annotated, TypedDict
from .prompt_loader import PromptLoader
from langgraph.graph import START, StateGraph, END
from langgraph.graph.message import add_messages
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder

from ..model import model

prompt_loader = PromptLoader()
persona_prompt = prompt_loader.get_prompt("mentor_persona")


class State(TypedDict):
    messages: Annotated[list, add_messages]

    status_started: bool 
    status_completed: bool

    impediments_started: bool
    impediments_completed: bool

    promises_started: bool
    promises_completed: bool

    summary_started: bool
    summary_completed: bool


def mentor(state: State):
    prompt = ChatPromptTemplate(
        [
            ("system", persona_prompt),
            MessagesPlaceholder("messages"),
        ]
    )
    chain = prompt | model
    return {"messages": [chain.invoke({"messages": state["messages"]})]}


def greeting(state: State):
    prompt = ChatPromptTemplate(
        [
            ("system", persona_prompt),
            (
                "system",
                "Greet the user warmly and express excitement about starting today’s session. Keep the greeting friendly and encouraging. Mention that you are here to support them and look forward to making progress together.",
            ),
        ]
    )
    chain = prompt | model
    return {"messages": [chain.invoke({"messages": state["messages"]})]}


def check_state(state: State):
    if state["status_started"] and not state["status_completed"]:
        step = "status"
    elif state["impediments_started"] and not state["impediments_completed"]:
        step = "impediments"
    elif state["promises_started"] and not state["promises_completed"]:
        step = "promises"
    elif state["summary_started"] and not state["summary_completed"]:
        step = "summary"
    else:
        step = "mentor"

    prompt = ChatPromptTemplate(
        [
            ("system", prompt_loader.get_prompt("state_check").format(step)),
            MessagesPlaceholder("messages"),
        ]
    )

    chain = prompt | model
    if chain.invoke({"messages": state["messages"]}).content == "YES":
        if step == "status":
            state["status_completed"] = True
        elif step == "impediments":
            state["impediments_completed"] = True
        elif step == "promises":
            state["promises_completed"] = True
        elif step == "summary":
            state["summary_completed"] = True
    
    return {"messages": state["messages"]}


def status(state: State):
    prompt = ChatPromptTemplate(
        (
            "system",
            "Analyze the conversation. If you haven't already, ask the user how the project is going. If you have already asked, ask if there is anything left that user wants to share about the project status.",
        )
    )
    chain = prompt | model
    return {"messages": [chain.invoke({"messages": state["messages"]})]}


def impediments(state: State):
    prompt = ChatPromptTemplate(
        (
            "system",
            "Analyze the conversation. If you haven't already, ask the user if they are facing any impediments. If you have already asked, ask if there is anything else the user wants to share about their impediments.",
        )
    )
    chain = prompt | model
    return {"messages": [chain.invoke({"messages": state["messages"]})]}


def promises(state: State):
    prompt = ChatPromptTemplate(
        (
            "system",
            "Analyze the conversation. If you haven't already, ask the user about any promises they can make regarding the project. If you have already asked, ask if there is anything else the user wants to share about their promises.",
        )
    )

    chain = prompt | model
    return {"messages": [chain.invoke({"messages": state["messages"]})]}


def summary(state: State):
    prompt = ChatPromptTemplate(
        (
            "system",
            "Analyze the conversation. If you haven't already, ask the user to provide a summary of the project. If you have already asked, ask if there is anything else the user wants to share about the project summary.",
        )
    )
    chain = prompt | model
    return {"messages": [chain.invoke({"messages": state["messages"]})]}


# router defines the next step in the conversation
def router(state: State):
    if len(state["messages"]) == 0:
        return "greeting"
    elif state["status_started"] and not state["status_completed"]:
        return "status"
    elif state["impediments_started"] and not state["impediments_completed"]:
        return "impediments"
    elif state["promises_started"] and not state["promises_completed"]:
        return "promises"
    elif state["summary_started"] and not state["summary_completed"]:
        return "summary"
    else:
        return "mentor"


graph_builder = StateGraph(State)
graph_builder.add_node("greeting", greeting)
graph_builder.add_node("status", status)
graph_builder.add_node("impediments", impediments)
graph_builder.add_node("promises", promises)
graph_builder.add_node("summary", summary)
graph_builder.add_node("mentor", mentor)
graph_builder.add_node("check_state", check_state)

graph_builder.add_conditional_edges(START, router)
graph_builder.add_edge("greeting", END)
graph_builder.add_edge("status", "check_state")
graph_builder.add_edge("impediments", "check_state")
graph_builder.add_edge("promises", "check_state")
graph_builder.add_edge("summary", "check_state")
graph_builder.add_edge("check_state", END)

graph = graph_builder.compile()
