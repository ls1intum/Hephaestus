"""
LangGraph-based mentor implementation with comprehensive development assistance tools.
"""

import json
from typing import Dict, Any, List
from langchain_core.messages import (
    SystemMessage,
    HumanMessage,
    AIMessage,
    ToolMessage,
    BaseMessage,
)
from langgraph.graph import StateGraph, START, END
from langgraph.prebuilt import tools_condition
from langchain_core.runnables import RunnableConfig
from langchain.chat_models import init_chat_model

from app.mentor.tools import (
    get_weather,
    create_document,
    update_document,
    get_issues,
    get_pull_requests,
    get_issue_details,
    get_pull_request_details,
    get_pull_request_bad_practices,
)
from app.models import get_model
from app.settings import settings
from app.logger import logger
from app.mentor.state import MentorState
from langgraph.prebuilt import ToolNode


# Get the chat model
ChatModel = get_model(settings.MODEL_NAME)
model = init_chat_model(settings.MODEL_NAME, reasoning={"summary": "auto"})

# Enhanced system prompt for the comprehensive mentor
SYSTEM_PROMPT = """\
You are an experienced software development mentor and coach, designed to help development teams work more effectively. Your role is to:

🎯 **CORE MISSION**: Help developers and teams break down complex work into manageable, deliverable chunks while maintaining high code quality and following best practices.

🛠️ **CAPABILITIES**:
- **Project Guidance**: Help break down large features into smaller, implementable tasks
- **Code Quality**: Analyze pull requests for best practices and potential issues  
- **Issue Management**: Review and prioritize bugs, features, and technical debt
- **Team Coaching**: Provide guidance on development workflows and collaboration
- **Weather Info**: Check weather conditions (you're located in Munich, Germany)

📋 **AVAILABLE TOOLS**: Strongly consider making use of them, think how we could help the user:
- `get_issues()` - Fetch regular GitHub issues (bugs, features, tasks)
- `get_pull_requests()` - Fetch pull requests for code review and analysis
- `get_issue_details(issue_ids)` - Get detailed info for specific issues
- `get_pull_request_details(pr_ids)` - Get detailed info for specific PRs  
- `get_pull_request_bad_practices(pr_id)` - Analyze bad practices in pull requests
- `get_weather()` - Weather information

🎨 **COMMUNICATION STYLE**:
Think of yourself as a friendly, knowledgeable senior engineer who is:
- **Clear and supportive** in guidance
- **Encouraging and playful** when celebrating progress  
- **Always approachable** to make learning fun
- **Practical and actionable** in suggestions

🔄 **WORKFLOW APPROACH**:
1. **Assess Current State**: What are you working on? What's blocking you?
2. **Break Down Complexity**: Help identify smaller, manageable pieces
3. **Prioritize Value**: Focus on what delivers value to users quickly
4. **Quality Checks**: Ensure code quality and best practices
5. **Celebrate Progress**: Acknowledge wins and improvements

Remember: The goal is continuous delivery of value, not perfection. Help teams make steady progress while building good habits.

After using a tool, provide insights and next steps rather than just repeating the tool output.
"""


def create_mentor_graph():
    """Create a LangGraph for mentor interactions with comprehensive tools."""

    # All available tools for the mentor
    tools = [
        get_weather,
        create_document,
        update_document,
        get_issues,
        get_pull_requests,
        get_issue_details,
        get_pull_request_details,
        get_pull_request_bad_practices,
    ]
    model_with_tools = model.bind_tools(tools, tool_choice="auto")

    def call_model(state: MentorState, config: RunnableConfig):
        """Call the language model with enhanced context awareness."""
        messages = state["messages"]

        # Add system prompt if not present
        if not messages or not isinstance(messages[0], SystemMessage):
            messages = [SystemMessage(content=SYSTEM_PROMPT)] + messages

        # Add user context if available
        user_id = state.get("user_id")
        if user_id and len(messages) == 1:  # Only system message, first interaction
            context_msg = HumanMessage(
                content=f"User ID: {user_id}. Please introduce yourself and offer to help with their development work."
            )
            messages.append(context_msg)

        logger.debug(
            json.dumps(
                [
                    {
                        "type": msg.__class__.__name__,
                        "content": (
                            msg.content[:200] + "..."
                            if len(str(msg.content)) > 200
                            else str(msg.content)
                        ),
                    }
                    for msg in messages
                ],
                indent=2,
                ensure_ascii=False,
            )
        )

        logger.info(
            f"Calling model with {len(messages)} messages for user {state.get('user_id')}"
        )
        response = model_with_tools.invoke(messages, config)
        return {"messages": [response]}

    def should_continue(state: MentorState):
        """Decide whether to continue to tools or end."""
        messages = state["messages"]
        last_message = messages[-1]

        # If the last message has tool calls, continue to tools
        if hasattr(last_message, "tool_calls") and last_message.tool_calls:
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
    workflow.add_conditional_edges(
        "agent", should_continue, {"tools": "tools", END: END}
    )
    workflow.add_edge("tools", "agent")

    return workflow.compile()


mentor_graph = create_mentor_graph()
