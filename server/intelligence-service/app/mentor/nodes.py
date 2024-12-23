from .state import State
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from ..model import model
from .prompt_loader import PromptLoader

prompt_loader = PromptLoader()
persona_prompt = prompt_loader.get_prompt("mentor_persona")


def mentor(state: State):
    prompt = ChatPromptTemplate(
        [
            ("system", persona_prompt),
            MessagesPlaceholder("messages"),
        ]
    )
    chain = prompt | model
    return {"messages": [chain.invoke({"messages": state["messages"]})]}

# greeting is the first node to be called in the graph: it initializes the full state
def greeting(state: State):
    prompt = ChatPromptTemplate(
        [
            ("system", persona_prompt),
            (
                "system", prompt_loader.get_prompt("greeting")
            ),
            MessagesPlaceholder("messages"),
        ]
    )
    chain = prompt | model
    return {
        "messages": [chain.invoke({"messages": state["messages"]})],
        "status": True,
        "impediments": False,
        "promises": False,
        "summary": False,
    }


def ask_status(state: State):
    prompt = ChatPromptTemplate(
        [
            #("system", persona_prompt),
            (
                "system", prompt_loader.get_prompt("status")
            ),
            MessagesPlaceholder("messages"),
        ]
    )
    print("-------------------------------------------------------------------------------")
    chain = prompt | model
    return {"messages": [chain.invoke({"messages": state["messages"]})]}


def ask_impediments(state: State):
    prompt = ChatPromptTemplate(
        [
            #("system", persona_prompt),
            (
                "system", prompt_loader.get_prompt("impediments")
            ),
            MessagesPlaceholder("messages"),
        ]
    )
    chain = prompt | model
    return {"messages": [chain.invoke({"messages": state["messages"]})]}


def ask_promises(state: State):
    prompt = ChatPromptTemplate(
        [
            #("system", persona_prompt),
            (
                "system", prompt_loader.get_prompt("promises")
            ),
            MessagesPlaceholder("messages"),
        ]
    )
    chain = prompt | model
    return {"messages": [chain.invoke({"messages": state["messages"]})]}


def ask_summary(state: State):
    prompt = ChatPromptTemplate(
        [
            #("system", persona_prompt),
            (
                "system", prompt_loader.get_prompt("summary")
            ),
            MessagesPlaceholder("messages"),
        ]
    )
    chain = prompt | model
    return {"messages": [chain.invoke({"messages": state["messages"]})]}


def check_state(state: State):
    if state["status"]:
        step = "status"
    elif state["impediments"]:
        step = "impediments"
    elif state["promises"]:
        step = "promises"
    elif state["summary"]:
        step = "summary"
    else:
        return
    
    print("step: ", step)
    
    prompt = ChatPromptTemplate(
        [
            ("system", prompt_loader.get_prompt("check_state").format_map({"step": step})),
            MessagesPlaceholder("messages"),
        ]
    )

    chain = prompt | model
    resp = chain.invoke({"messages": state["messages"]}).content

    print("check_state", resp)
   
    if resp == "YES":
        if step == "status":
            print("\n----------------HEY! the state[status] changed----------------\n")
            return {"status": False, "impediments": True}
        elif step == "impediments":
            print("\n----------------HEY! the state[impediments] changed----------------\n")
            return {"impediments": False, "promises": True}
        elif step == "promises":
            print("\n----------------HEY! the state[promises] changed----------------\n")
            return {"promises": False, "summary": True}
        elif step == "summary":
            print("\n----------------HEY! the state[summary] changed----------------\n")
            return {"summary": False}
    return
