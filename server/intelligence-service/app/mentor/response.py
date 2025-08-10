"""
LangGraph-based response generation with proper streaming using astream_events.
"""

import json
import uuid
from typing import AsyncGenerator
from collections import deque
from langchain_core.messages import BaseMessage
from langchain_core.runnables import RunnableConfig

from app.mentor.streaming import StreamGenerator
from app.mentor.mentor import mentor_graph
from app.logger import logger


async def generate_response(
    messages: list[BaseMessage],
    user_id: int | None = None,
    stream: StreamGenerator | None = None,
) -> AsyncGenerator[str, None]:
    """
    Generate a response using LangGraph with proper streaming via astream_events.

    Args:
        messages: List of LangChain messages
        user_id: Optional user ID for context
        stream: StreamGenerator instance for formatting events

    Yields:
        Formatted stream events as strings
    """

    try:
        logger.debug(f"Received user_id: {user_id}")

        # Prepare initial state with user_id properly included
        initial_state = {
            "messages": messages,
            "user_id": user_id,
        }

        logger.debug(f"Initial state: {initial_state}")

        # Configuration for the run with thread_id
        config = RunnableConfig(configurable={"thread_id": f"user_{user_id}"})

        logger.debug(f"Starting LangGraph astream_events for user {user_id}")

        # Track tool calls for proper streaming
        active_tool_calls: dict[str, dict] = {}
        tool_call_queue_by_name: dict[str, deque[str]] = {}
        # Map call-id -> name and index -> call-id for chunk correlation
        name_by_call_id: dict[str, str] = {}
        index_to_call_id: dict[int, str] = {}
        text_stream_id: str | None = None
        text_streaming_active = False
        reasoning_stream_id: str | None = None
        reasoning_streaming_active = False

        # Stream events from the graph
        async for event in mentor_graph.astream_events(initial_state, config):
            event_type = event.get("event")
            name = event.get("name", "")
            event_data = event.get("data", {})
            metadata = event.get("metadata", {})
            node_name = metadata.get("langgraph_node", "")
            chunk = event_data.get("chunk")

            logger.debug(f"Graph event: {event_type} - {name} - Node: {node_name}")
            # try:
            #     logger.debug(f"Graph event data: {json.dumps(event, indent=2)}")
            # except Exception as e:
            #     pass

            # Handle chat model streaming (text + reasoning generation)
            if event_type == "on_chat_model_stream" and node_name == "agent" and chunk:
                # Handle provider reasoning (if present in additional_kwargs)
                try:
                    add_kwargs = getattr(chunk, "additional_kwargs", {}) or {}
                    reasoning = add_kwargs.get("reasoning")
                    if isinstance(reasoning, dict):
                        # Start reasoning stream if not active
                        if not reasoning_streaming_active:
                            reasoning_stream_id = reasoning.get("id") or str(
                                uuid.uuid4()
                            )
                            yield stream.reasoning_start(reasoning_stream_id)
                            reasoning_streaming_active = True
                        # Stream reasoning summary deltas (provider specific incremental tokens)
                        summary = reasoning.get("summary")
                        if isinstance(summary, list) and summary:
                            # Extract texts from summary entries
                            parts = []
                            for s in summary:
                                if isinstance(s, dict):
                                    t = s.get("text")
                                    if isinstance(t, str) and t:
                                        parts.append(t)
                            if parts:
                                yield stream.reasoning_delta(
                                    reasoning_stream_id, "".join(parts)
                                )
                except Exception:
                    # Never fail due to optional reasoning handling
                    pass

                # Handle text content with ID-based streaming
                if hasattr(chunk, "content") and chunk.content is not None:
                    text_delta = ""
                    if isinstance(chunk.content, str):
                        text_delta = chunk.content
                    elif isinstance(chunk.content, list):
                        # Collect 'text' from content parts
                        for part in chunk.content:
                            if isinstance(part, dict) and part.get("type") == "text":
                                part_text = part.get("text")
                                if isinstance(part_text, str):
                                    text_delta += part_text
                    if text_delta:
                        if not text_streaming_active:
                            text_stream_id = str(uuid.uuid4())
                            yield stream.text_start(text_stream_id)
                            text_streaming_active = True
                        yield stream.text_delta(text_stream_id, text_delta)

                # Prefer true chunked tool-call streaming when available
                used_tool_call_chunks = False
                if hasattr(chunk, "tool_call_chunks") and chunk.tool_call_chunks:
                    for tcc in chunk.tool_call_chunks:
                        try:
                            idx = tcc.get("index")
                            tcc_id = tcc.get("id")
                            tcc_name = tcc.get("name")
                            # Remember mapping index -> id when first seen
                            if isinstance(idx, int) and tcc_id:
                                index_to_call_id[idx] = tcc_id
                            # Resolve call id using id or prior index mapping
                            tool_call_id = tcc_id
                            if tool_call_id is None and isinstance(idx, int):
                                tool_call_id = index_to_call_id.get(idx)
                            # Resolve tool name
                            tool_name = tcc_name
                            if tool_name and tool_call_id:
                                name_by_call_id[tool_call_id] = tool_name
                            if tool_name is None and tool_call_id:
                                tool_name = name_by_call_id.get(tool_call_id)

                            if tool_call_id:
                                # Initialize tracking + queue for on_tool_end correlation
                                if tool_call_id not in active_tool_calls:
                                    active_tool_calls[tool_call_id] = {
                                        "name": tool_name,
                                        "args": {},
                                        "started": False,
                                    }
                                    if tool_name:
                                        q = tool_call_queue_by_name.setdefault(
                                            tool_name, deque()
                                        )
                                        q.append(tool_call_id)

                                # Emit tool-input-start once per call
                                if (
                                    not active_tool_calls[tool_call_id]["started"]
                                    and tool_name
                                ):
                                    yield stream.tool_input_start(
                                        tool_call_id=tool_call_id,
                                        tool_name=tool_name,
                                    )
                                    active_tool_calls[tool_call_id]["started"] = True

                                # Stream the raw args delta text as produced by the model
                                delta_text = tcc.get("args")
                                if isinstance(delta_text, str) and delta_text:
                                    yield stream.tool_input_delta(
                                        tool_call_id=tool_call_id,
                                        input_text_delta=delta_text,
                                    )
                                    used_tool_call_chunks = True
                        except Exception:
                            # Tool-call streaming is best-effort; do not break the stream
                            pass

                # Fallback: derive deltas from aggregated tool_call args if no chunks provided
                if (
                    not used_tool_call_chunks
                    and hasattr(chunk, "tool_calls")
                    and chunk.tool_calls
                ):
                    for tool_call in chunk.tool_calls:
                        tool_call_id = tool_call.get("id")
                        tool_name = tool_call.get("name")
                        tool_args = tool_call.get("args", {})

                        if tool_call_id:
                            if tool_name:
                                name_by_call_id[tool_call_id] = tool_name
                            if tool_call_id not in active_tool_calls:
                                active_tool_calls[tool_call_id] = {
                                    "name": tool_name,
                                    "args": {},
                                    "started": False,
                                }
                                if tool_name:
                                    q = tool_call_queue_by_name.setdefault(
                                        tool_name, deque()
                                    )
                                    q.append(tool_call_id)

                            if (
                                not active_tool_calls[tool_call_id]["started"]
                                and tool_name
                            ):
                                yield stream.tool_input_start(
                                    tool_call_id=tool_call_id,
                                    tool_name=tool_name,
                                )
                                active_tool_calls[tool_call_id]["started"] = True

                            current_args = active_tool_calls[tool_call_id]["args"]
                            if tool_args != current_args:
                                new_args_json = json.dumps(
                                    tool_args, separators=(",", ":")
                                )
                                current_args_json = json.dumps(
                                    current_args, separators=(",", ":")
                                )
                                if len(new_args_json) > len(current_args_json):
                                    delta = new_args_json[len(current_args_json):]
                                    if delta:
                                        yield stream.tool_input_delta(
                                            tool_call_id=tool_call_id,
                                            input_text_delta=delta,
                                        )
                                active_tool_calls[tool_call_id]["args"] = tool_args

            # Handle when chat model finishes (tool calls are complete)
            elif event_type == "on_chat_model_end" and node_name == "agent":
                if text_streaming_active and text_stream_id:
                    yield stream.text_end(text_stream_id)
                    text_streaming_active = False
                    text_stream_id = None
                if reasoning_streaming_active and reasoning_stream_id:
                    yield stream.reasoning_end(reasoning_stream_id)
                    reasoning_streaming_active = False
                    reasoning_stream_id = None

                output = event_data.get("output")
                if output and hasattr(output, "tool_calls") and output.tool_calls:
                    for tool_call in output.tool_calls:
                        tool_call_id = tool_call.get("id")
                        tool_name = tool_call.get("name")
                        tool_args = tool_call.get("args", {})

                        if tool_call_id:
                            yield stream.tool_input_available(
                                tool_call_id=tool_call_id,
                                tool_name=tool_name,
                                input_data=tool_args,
                            )

            # Handle tool execution start
            elif event_type == "on_tool_start" and node_name == "tools":
                tool_name = name or event_data.get("input", {}).get("name", "")
                logger.debug(f"Tool execution started: {tool_name}")

            # Handle tool execution end
            elif event_type == "on_tool_end" and node_name == "tools":
                tool_name = name or event_data.get("input", {}).get("name", "")
                output = event_data.get("output")
                logger.debug(f"Tool execution completed: {tool_name}")

                tool_call_id = None
                q = tool_call_queue_by_name.get(tool_name)
                if q and len(q) > 0:
                    tool_call_id = q.popleft()

                if output is not None and tool_call_id:
                    logger.debug(f"Processing tool output of type: {type(output)}")
                    logger.debug(f"Tool output: {output}")

                    output_data = None
                    if hasattr(output, "content"):
                        content = output.content
                        if isinstance(content, str):
                            try:
                                parsed_content = json.loads(content)
                                output_data = (
                                    parsed_content
                                    if isinstance(parsed_content, (dict, list))
                                    else {"result": parsed_content}
                                )
                            except json.JSONDecodeError:
                                output_data = {"result": content}
                        elif isinstance(content, (dict, list)):
                            output_data = content
                        else:
                            output_data = {"result": str(content)}
                    elif isinstance(output, str):
                        try:
                            parsed_output = json.loads(output)
                            output_data = (
                                parsed_output
                                if isinstance(parsed_output, (dict, list))
                                else {"result": parsed_output}
                            )
                        except json.JSONDecodeError:
                            output_data = {"result": output}
                    elif isinstance(output, list):
                        output_data = {"items": output, "count": len(output)}
                    elif isinstance(output, dict):
                        output_data = output
                    else:
                        output_data = {"result": str(output)}

                    yield stream.tool_output_available(
                        tool_call_id=tool_call_id,
                        output_data=output_data,
                    )

            # Data parts handling
            elif event_type == "on_custom_event":
                name = event.get("name", "")
                if name.startswith("data-"):
                    event_data = event.get("data", {})
                    data_id = event_data.get("id")
                    transient = event_data.get("transient", False)
                    data_content = event_data.get("data", {})

                    yield stream.data(
                        data_type=name.replace("data-", ""),
                        data_content=data_content,
                        transient=transient,
                        data_id=data_id,
                    )

                logger.debug(f"on_custom_event event: {json.dumps(event, indent=2)}")

            # Handle any errors
            elif event_type == "on_chain_error":
                error_msg = (
                    f"Error in {name}: {event_data.get('error', 'Unknown error')}"
                )
                logger.error(error_msg)
                yield stream.error(error_msg)

        logger.debug("LangGraph astream_events completed")

    except Exception as e:
        logger.error(f"Error in LangGraph response generation: {str(e)}")
        yield stream.error(f"Error generating response: {str(e)}")
