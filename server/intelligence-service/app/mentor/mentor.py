"""
LangGraph-based mentor implementation.
"""
import json
from typing import Dict, Any, List
from langchain_core.messages import SystemMessage, HumanMessage, AIMessage, ToolMessage, BaseMessage
from langgraph.graph import StateGraph, START, END
from langgraph.prebuilt import tools_condition
from langchain_core.runnables import RunnableConfig

from app.mentor.tools import get_weather, get_user_issues
from app.models import get_model
from app.settings import settings
from app.logger import logger
from app.mentor.state import MentorState
from langgraph.prebuilt import ToolNode


# Get the chat model
ChatModel = get_model(settings.MODEL_NAME)
model = ChatModel(temperature=0.7, max_tokens=4096)

# System prompt for the mentor
SYSTEM_PROMPT = """\
You are a helpful development mentor. I can assist you with:

- Development questions and code reviews
- Your current issues and pull requests 
- Weather information (you're located in Munich, Germany)
- Technical guidance and best practices

After using a tool, NEVER repeat the output of the last message.
"""


def create_mentor_graph():
    """Create a LangGraph for mentor interactions."""
    
    # Available tools
    tools = [get_weather, get_user_issues]
    model_with_tools = model.bind_tools(tools)
    
    def call_model(state: MentorState, config: RunnableConfig):
        """Call the language model."""
        messages = state["messages"]
                
        # Add system prompt if not present
        if not messages or not isinstance(messages[0], SystemMessage):
            messages = [SystemMessage(content=SYSTEM_PROMPT)] + messages
        
        if not messages or not isinstance(messages[-1], HumanMessage):
            messages.append(HumanMessage(content="The reponse above is shown to the user in the UI. Please do not repeat it. Instead, stear the conversation towards the next step."))
        
        logger.debug(json.dumps([msg.model_dump() for msg in messages], indent=2, ensure_ascii=False))
        
        logger.info(f"Calling model with {len(messages)} messages for user {state.get('user_id')}")
        response = model_with_tools.invoke(messages, config)
        return {"messages": [response]}
    
    def should_continue(state: MentorState):
        """Decide whether to continue to tools or end."""
        messages = state["messages"]
        last_message = messages[-1]
        
        # If the last message has tool calls, continue to tools
        if hasattr(last_message, 'tool_calls') and last_message.tool_calls:
            return "tools"
        
        # Otherwise, end
        return END
    
    # Create the graph
    workflow = StateGraph(MentorState)
    
    # Add nodes
    workflow.add_node("agent", call_model)
    workflow.add_node("tools", ToolNode(tools))
    
    # Add edges
    workflow.add_edge(START, "agent")
    workflow.add_conditional_edges("agent", should_continue, {"tools": "tools", END: END})
    workflow.add_edge("tools", "agent")
    
    return workflow.compile()


mentor_graph = create_mentor_graph()