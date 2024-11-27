import os
from .config import settings
from typing import Sequence
from typing_extensions import Annotated, TypedDict

from langchain_core.messages import trim_messages, BaseMessage, HumanMessage
from langchain.chat_models.base import BaseChatModel
from langchain_openai import ChatOpenAI, AzureChatOpenAI
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import START, StateGraph
from langgraph.graph.message import add_messages


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
    messages: Annotated[Sequence[BaseMessage], add_messages]


mentor_prompt = ChatPromptTemplate.from_messages(
    [
        (
            "system",
            "You are an AI mentor helping a students working on the software engineering projects embracing structured self-reflection practices."
            + "You need to guide the student through the set questions regarding their work on the project during the last week (sprint). Your value is the fact, that you help students to reflect on their past progress."
            + "Throughout the conversation you need to perform all of the following tasks in the given order: "
            + "Task 1: Greet the student and say you are happy to start the session."
            + "Task 2: Ask the student about the overall progress on the project."
            + "Task 3: Ask the student about the challenges faced during the sprint referring to what he said about progress."
            + "Task 4: Ask about the plan for the next sprint."
            + "Be polite, friendly and do not let the student drive the conversation to any other topic except for the current project."
            + "Always perform only one task in one message. Listen to what the student says and refer to it in your next message."
            + "Analyse the conversation before and asses which task is to be performed. Give the student a feeling of a real conversation, not just questionaire.",
        ),
        MessagesPlaceholder(variable_name="messages"),
    ]
)

workflow = StateGraph(state_schema=State)
trimmer = trim_messages(
    max_tokens=4000,
    strategy="last",
    token_counter=model,
    include_system=True,
    allow_partial=False,
    start_on="human",
)


def call_model(state: State):
    chain = mentor_prompt | model
    trimmed_messages = trimmer.invoke(state["messages"])
    response = chain.invoke({"messages": trimmed_messages})
    return {"messages": [response]}


workflow.add_edge(START, "model")
workflow.add_node("model", call_model)

memory = MemorySaver()
app = workflow.compile(checkpointer=memory)


def send_message(thread_id: str, input_message: str, state: State):
    config = {"configurable": {"thread_id": thread_id}}
    state["messages"] += [HumanMessage(input_message)]

    output = app.invoke(
        {"messages": state["messages"]},
        config,
    )

    state["messages"] += output.get("messages", [])
    return {"state": state, "response": output}