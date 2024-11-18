import os
from .config import settings
from random import randint
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
    model = ChatOpenAI()
elif settings.is_azure_openai_available:
    model = AzureChatOpenAI()
else:
    raise EnvironmentError("No LLM available")


class State(TypedDict):
    messages: Annotated[Sequence[BaseMessage], add_messages]
    step: int


mentor_prompt = ChatPromptTemplate.from_messages(
    [
        (
            "system",
            "You are an AI mentor helping a students working on the software engineering projects."
            + "You need to guide the student through the set of three questions regarding their work on the project during the last week (sprint)."
            + "Steps are the indicator of your current task in the conversation with the student. Your current step is {step}. Just follow the instructions and focus on the current step."
            + "If your step is 0: greet the student and say you are happy to start the reflective session."
            + "If your step is 1: ask the student about the overall progress on the project."
            + "If your step is 2: ask the student about the challenges faced during the sprint reffering to what he saied about progress."
            + "If your step is 3: ask about the plan for the next sprint."
            + "If your step is >3: continue the conversation trying to assist the student.",
        ),
        MessagesPlaceholder(variable_name="messages"),
    ]
)

workflow = StateGraph(state_schema=State)
trimmer = trim_messages(
    max_tokens=400,
    strategy="last",
    token_counter=model,
    include_system=True,
    allow_partial=False,
    start_on="human", # TODO: change to "system"
)


def call_model(state: State):
    chain = mentor_prompt | model
    trimmed_messages = trimmer.invoke(state["messages"])
    response = chain.invoke({"messages": trimmed_messages, "step": state["step"]})
    return {"messages": [response]}


workflow.add_edge(START, "model")
workflow.add_node("model", call_model)

memory = MemorySaver()
app = workflow.compile(checkpointer=memory)


def chat(thread_id: str, input_message: str, state: State):
    config = {"configurable": {"thread_id": thread_id}}
    # append the new human message to the conversation
    state["messages"] += [HumanMessage(input_message)]
    state["step"] += 1

    output = app.invoke(
        {"messages": state["messages"], "step": state["step"]},
        config,
    )

    state["messages"] += output.get("messages", [])
    return {"state": state, "response": output}
