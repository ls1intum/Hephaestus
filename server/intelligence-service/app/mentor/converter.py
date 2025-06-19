import json
from typing import List
from langchain_core.messages import HumanMessage, AIMessage, SystemMessage, ToolMessage

from app.mentor.models import (
    TextUIPart,
    ToolInputAvailablePart,
    ToolOutputAvailablePart,
    UIMessage,
)


def convert_to_langchain_messages(messages: List[UIMessage]):
    """Convert UIMessage objects to LangChain message objects."""
    langchain_messages = []

    for msg in messages:
        # Extract text content from parts
        content = ""
        tool_calls = []
        tool_call_id = None

        for part in msg.parts:
            if isinstance(part, TextUIPart):
                content += part.text
            elif isinstance(part, (ToolInputAvailablePart, ToolOutputAvailablePart)):
                # Handle tool call parts for assistant messages
                if msg.role == "assistant" and isinstance(part, ToolInputAvailablePart):
                    tool_calls.append(
                        {
                            "id": part.toolCallId,
                            "name": getattr(part, "toolName", "unknown"),
                            "args": part.input,
                        }
                    )
                # Handle tool results for tool messages
                elif isinstance(part, ToolOutputAvailablePart):
                    tool_call_id = part.toolCallId
                    content = json.dumps(part.output) if part.output else ""

        if msg.role == "user":
            langchain_messages.append(HumanMessage(content=content))
        elif msg.role == "assistant":
            if tool_calls:
                langchain_messages.append(
                    AIMessage(content=content, tool_calls=tool_calls)
                )
            else:
                langchain_messages.append(AIMessage(content=content))
        elif msg.role == "system":
            langchain_messages.append(SystemMessage(content=content))
        elif tool_call_id:  # Tool message
            langchain_messages.append(
                ToolMessage(content=content, tool_call_id=tool_call_id)
            )

    return langchain_messages
