import json
from typing import List, Any, Optional
from pydantic import BaseModel
from langchain_core.messages import HumanMessage, AIMessage, SystemMessage, ToolMessage

from app.mentor.models import (
    TextUIPart,
    ToolInputAvailablePart,
    ToolOutputAvailablePart,
    UIMessage,
)
from app.logger import logger


def convert_to_langchain_messages(messages: List[UIMessage]):
    """Convert UIMessage objects to LangChain message objects.

    Produces a sequence compatible with LangChain's tool calling format:
    - AIMessage(tool_calls=[{id, name, args}]) when tools are requested
    - ToolMessage(content=..., tool_call_id=...) when tool outputs are available
    - HumanMessage/SystemMessage/AIMessage for plain text parts
    """
    logger.debug("Converting UIMessage objects to LangChain messages")
    try:
        logger.debug(json.dumps([msg.model_dump() for msg in messages], indent=2))
    except Exception:
        # Be defensive in case model_dump is unavailable
        logger.debug("[converter] Received %d messages", len(messages))

    langchain_messages = []

    def _normalize_tool_output(output: Any) -> str:
        if output is None:
            return ""
        if isinstance(output, BaseModel):
            return json.dumps(output.model_dump(exclude_none=True))
        if isinstance(output, (dict, list)):
            return json.dumps(output)
        if isinstance(output, str):
            return output
        return json.dumps({"result": str(output)})

    emitted_tool_call_ids: set[str] = set()

    for msg in messages:
        role = getattr(msg, "role", None)
        parts = getattr(msg, "parts", []) or []

        # Buffers to allow splitting a single UIMessage into multiple LangChain messages
        assistant_text_buffer: str = ""
        user_text_buffer: str = ""
        system_text_buffer: str = ""
        pending_tool_calls: list[dict] = []

        def flush_assistant_text():
            nonlocal assistant_text_buffer
            if assistant_text_buffer:
                langchain_messages.append(AIMessage(content=assistant_text_buffer))
                assistant_text_buffer = ""

        def flush_tool_calls():
            nonlocal pending_tool_calls
            if pending_tool_calls:
                # content can be empty when tool_calls are present
                langchain_messages.append(
                    AIMessage(content="", tool_calls=pending_tool_calls)
                )
                # track emitted ids
                for tc in pending_tool_calls:
                    tc_id = tc.get("id")
                    if isinstance(tc_id, str) and tc_id:
                        emitted_tool_call_ids.add(tc_id)
                pending_tool_calls = []

        for part in parts:
            # Identify simple text first
            if isinstance(part, TextUIPart):
                text = getattr(part, "text", "") or ""
                if role == "assistant":
                    assistant_text_buffer += text
                elif role == "user":
                    user_text_buffer += text
                elif role == "system":
                    system_text_buffer += text
                continue

            # Robust tool part detection (supports both class checks and type/state fallbacks)
            is_tool_input = isinstance(part, ToolInputAvailablePart)
            is_tool_output = isinstance(part, ToolOutputAvailablePart)

            p_type: Optional[str] = getattr(part, "type", None)
            state: Optional[str] = getattr(part, "state", None)
            # Exclude reasoning parts entirely from the message history
            if p_type == "reasoning":
                continue
            if p_type and isinstance(p_type, str) and p_type.startswith("tool-"):
                # If classes aren't specific, infer from state
                if state == "input-available" and not is_tool_input:
                    is_tool_input = True
                if state == "output-available" and not is_tool_output:
                    is_tool_output = True

            if is_tool_input or is_tool_output:
                # Derive tool metadata
                tool_name = getattr(part, "toolName", None)
                if not tool_name and p_type and p_type.startswith("tool-"):
                    tool_name = p_type[len("tool-") :]
                if not tool_name:
                    tool_name = "unknown"

                tool_call_id = getattr(part, "toolCallId", None)
                tool_args = getattr(part, "input", None) or {}
                tool_output = getattr(part, "output", None)

                # If we have inputs, add a pending tool call request
                if is_tool_input or (tool_args and not is_tool_output):
                    if role == "assistant":
                        tc_id = str(tool_call_id) if tool_call_id is not None else None
                        pending_tool_calls.append(
                            {
                                "id": tc_id,
                                "name": tool_name,
                                "args": tool_args,
                                "type": "tool_call",
                            }
                        )
                    # If the tool input appears in a non-assistant role, we skip creating tool_calls

                # If we have output, emit ToolMessage in correct order
                if is_tool_output or tool_output is not None:
                    # Ensure the preceding tool call(s) are emitted to maintain association
                    flush_assistant_text()
                    if pending_tool_calls:
                        flush_tool_calls()

                    # If we haven't seen a prior tool_call for this id, synthesize one now
                    if not tool_call_id:
                        # Synthesize a stable id from name+args to keep pairing deterministic
                        synth_id = f"synth_{tool_name}_" + json.dumps(
                            tool_args, sort_keys=True
                        )
                        tool_call_id = synth_id
                    if str(tool_call_id) not in emitted_tool_call_ids:
                        langchain_messages.append(
                            AIMessage(
                                content="",
                                tool_calls=[
                                    {
                                        "id": str(tool_call_id),
                                        "name": tool_name,
                                        "args": tool_args or {},
                                        "type": "tool_call",
                                    }
                                ],
                            )
                        )
                        emitted_tool_call_ids.add(str(tool_call_id))
                    content = _normalize_tool_output(tool_output)
                    langchain_messages.append(
                        ToolMessage(content=content, tool_call_id=str(tool_call_id))
                    )

                continue

            # Fallback: any unknown part types with a text field
            text = getattr(part, "text", None)
            if isinstance(text, str) and text:
                if role == "assistant":
                    assistant_text_buffer += text
                elif role == "user":
                    user_text_buffer += text
                elif role == "system":
                    system_text_buffer += text

        # Flush any remaining buffers for this UIMessage
        if role == "user" and user_text_buffer:
            langchain_messages.append(HumanMessage(content=user_text_buffer))
        elif role == "system" and system_text_buffer:
            langchain_messages.append(SystemMessage(content=system_text_buffer))
        elif role == "assistant":
            # If there are any pending tool calls left, emit them first
            if pending_tool_calls:
                flush_tool_calls()
            if assistant_text_buffer:
                flush_assistant_text()

    return langchain_messages
