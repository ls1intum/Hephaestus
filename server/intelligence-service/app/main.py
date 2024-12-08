from fastapi import FastAPI
from pydantic import BaseModel
import os
from typing import List
from .config import settings
from typing_extensions import Annotated, TypedDict
from langchain.chat_models.base import BaseChatModel
from langchain_openai import ChatOpenAI, AzureChatOpenAI
from langgraph.graph import START, StateGraph, END
from langgraph.graph.message import add_messages
from langchain_core.messages import SystemMessage, HumanMessage, AIMessage

app = FastAPI(
    title="Hephaestus Intelligence Service API",
    description="API documentation for the Hephaestus Intelligence Service.",
    version="0.0.1",
    contact={"name": "Felix T.J. Dietrich", "email": "felixtj.dietrich@tum.de"},
)


class ChatMessage(BaseModel):
    sender: str
    content: str


class ChatRequest(BaseModel):
    message_history: List[ChatMessage]


class ChatResponse(BaseModel):
    responce: str


model: BaseChatModel

if os.getenv("GITHUB_ACTIONS") == "true":

    class MockChatModel:
        def invoke(self, message: str):
            return "Mock response"

    model = MockChatModel()

elif settings.is_openai_available:
    model = ChatOpenAI(temperature=1.0)
elif settings.is_azure_openai_available:
    model = AzureChatOpenAI()
else:
    raise EnvironmentError("No LLM available")


class State(TypedDict):
    messages: Annotated[list, add_messages]


prompt = "You are an AI mentor helping a students working on the software engineering projects embracing structured self-reflection practices. You need to guide the student through the set questions regarding their work on the project during the last week (sprint). Your value is the fact, that you help students to reflect on their past progress. Throughout the conversation you need to perform all of the following tasks in the given order: Task 1: Greet the student and say you are happy to start the session. Task 2: Ask the student about the overall progress on the project. Task 3: Ask the student about the challenges faced during the sprint referring to what he said about progress. Task 4: Ask about the plan for the next sprint. You need to understand at which task in the conversation you are from the message history and what is the next task. Please, don't repeat yourself throughout the conversation. Don't perform more then one task at a time. If the user already shared something to a task you can go to the next. Be polite, friendly and do not let the student drive the conversation to any other topic except for the current project. Do not make a questionaire out of the conversation, but rather make it a natural conversation. Don't repeat the answer of the student to your latest question but try to react on it. If the student asks questions be helpful and try to find solutions."


def ai_mentor(state: State):
    return {
        "messages": [model.invoke(state["messages"] + [SystemMessage(content=prompt)])]
    }


graph_builder = StateGraph(State)
graph_builder.add_node("ai_mentor", ai_mentor)
graph_builder.add_edge(START, "ai_mentor")
graph_builder.add_edge("ai_mentor", END)
graph = graph_builder.compile()


@app.post(
    "/chat",
    response_model=ChatResponse,
    summary="Start and continue a chat session with an LLM.",
)
def chat(request: ChatRequest):
    messages = []
    for message in request.message_history:
        if message.sender == "USER":
            messages.append(HumanMessage(content=message.content))
        else:
            messages.append(AIMessage(content=message.content))
    response_message = graph.invoke({"messages": messages})["messages"][-1].content
    return ChatResponse(responce=response_message)
