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


def mentor(state: State):
    prompt = ChatPromptTemplate(
        [
            ("system", persona_prompt),
            (
                "system",
                "You need to guide the student through the set questions regarding their work on the project during the last week (sprint). Your value is the fact, that you help students to reflect on their past progress. Throughout the conversation you need to perform all of the following tasks in the given order: Task 1: Ask the student about the overall progress on the project. Task 2: Ask the student about the challenges faced during the sprint referring to what he said about progress. Task 3: Ask about the plan for the next sprint. You need to understand at which task in the conversation you are from the message history and what is the next task. Please, don't repeat yourself throughout the conversation. Don't perform more then one task at a time. If the user already shared something to a task you can go to the next.",
            ),
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


def isFirstInteraction(state: State):
    if len(state["messages"]) == 0:
        return "greeting"
    return "mentor"


graph_builder = StateGraph(State)
graph_builder.add_node("mentor", mentor)
graph_builder.add_node("greeting", greeting)

graph_builder.add_conditional_edges(START, isFirstInteraction)
graph_builder.add_edge("mentor", END)
graph_builder.add_edge("greeting", END)

graph = graph_builder.compile()
