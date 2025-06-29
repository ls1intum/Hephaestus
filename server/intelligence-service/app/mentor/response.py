"""
LangGraph-based response generation with proper streaming using astream_events.
"""
import json
import uuid
from typing import AsyncGenerator
from langchain_core.messages import BaseMessage
from langchain_core.runnables import RunnableConfig

from app.mentor.streaming import StreamGenerator
from app.mentor.mentor import mentor_graph
from app.logger import logger


async def generate_response(
    messages: list[BaseMessage], 
    user_id: int = None, 
    stream: StreamGenerator = None
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
        logger.info(f"Received user_id: {user_id}")
        
        # Prepare initial state with user_id properly included
        initial_state = {
            "messages": messages,
            "user_id": user_id
        }
        
        logger.info(f"Initial state: {initial_state}")
        
        # Configuration for the run with thread_id
        config = RunnableConfig(
            configurable={"thread_id": f"user_{user_id}"}
        )
        
        logger.info(f"Starting LangGraph astream_events for user {user_id}")
        
        # Track tool calls for proper streaming
        active_tool_calls = {}
        tool_call_to_id = {}  # Map tool names to their call IDs for tracking
        text_stream_id = None
        text_streaming_active = False
        
        # Stream events from the graph
        async for event in mentor_graph.astream_events(
            initial_state,
            config,
        ):
            event_type = event.get("event")
            name = event.get("name", "")
            data = event.get("data", {})
            metadata = event.get("metadata", {})
            node_name = metadata.get("langgraph_node", "")
            
            logger.debug(f"Graph event: {event_type} - {name} - Node: {node_name}")
            
            # Handle chat model streaming (text generation)
            if event_type == "on_chat_model_stream" and node_name == "agent":
                chunk = data.get("chunk")
                if chunk:
                    # Handle text content with ID-based streaming
                    if hasattr(chunk, 'content') and chunk.content:
                        # Start text streaming if not already active
                        if not text_streaming_active:
                            text_stream_id = str(uuid.uuid4())
                            yield stream.text_start(text_stream_id)
                            text_streaming_active = True
                        
                        # Send text delta
                        yield stream.text_delta(text_stream_id, chunk.content)
                    
                    # Handle tool calls
                    if hasattr(chunk, 'tool_calls') and chunk.tool_calls:
                        for tool_call in chunk.tool_calls:
                            tool_call_id = tool_call.get("id")
                            tool_name = tool_call.get("name")
                            tool_args = tool_call.get("args", {})
                            
                            if tool_call_id:
                                # Start tool input if this is the first chunk for this tool call
                                if tool_call_id not in active_tool_calls:
                                    active_tool_calls[tool_call_id] = {
                                        "name": tool_name,
                                        "args": {},
                                        "started": False
                                    }
                                    # Map tool name to call ID for later lookup
                                    if tool_name:
                                        tool_call_to_id[tool_name] = tool_call_id
                                
                                # Check if we need to start the tool input stream
                                if not active_tool_calls[tool_call_id]["started"] and tool_name:
                                    yield stream.tool_input_start(
                                        tool_call_id=tool_call_id,
                                        tool_name=tool_name
                                    )
                                    active_tool_calls[tool_call_id]["started"] = True
                                
                                # Stream argument deltas if we have new args
                                current_args = active_tool_calls[tool_call_id]["args"]
                                if tool_args != current_args:
                                    # Convert new args to JSON and stream the difference
                                    new_args_json = json.dumps(tool_args)
                                    current_args_json = json.dumps(current_args)
                                    
                                    # Stream only the new part
                                    if len(new_args_json) > len(current_args_json):
                                        delta = new_args_json[len(current_args_json):]
                                        if delta:
                                            yield stream.tool_input_delta(
                                                tool_call_id=tool_call_id,
                                                input_text_delta=delta
                                            )
                                    
                                    # Update stored args
                                    active_tool_calls[tool_call_id]["args"] = tool_args
            
            # Handle when chat model finishes (tool calls are complete)
            elif event_type == "on_chat_model_end" and node_name == "agent":
                # End text streaming if it was active
                if text_streaming_active and text_stream_id:
                    yield stream.text_end(text_stream_id)
                    text_streaming_active = False
                    text_stream_id = None
                
                output = data.get("output")
                if output and hasattr(output, 'tool_calls') and output.tool_calls:
                    for tool_call in output.tool_calls:
                        tool_call_id = tool_call.get("id")
                        tool_name = tool_call.get("name")
                        tool_args = tool_call.get("args", {})
                        
                        if tool_call_id:
                            yield stream.tool_input_available(
                                tool_call_id=tool_call_id,
                                tool_name=tool_name,
                                input_data=tool_args
                            )
            
            # Handle tool execution start
            elif event_type == "on_tool_start" and node_name == "tools":
                tool_name = name or data.get("input", {}).get("name", "")
                logger.info(f"Tool execution started: {tool_name}")
            
            # Handle tool execution end
            elif event_type == "on_tool_end" and node_name == "tools":
                tool_name = name or data.get("input", {}).get("name", "")
                output = data.get("output")
                logger.info(f"Tool execution completed: {tool_name}")
                
                # Find the corresponding tool call ID
                tool_call_id = tool_call_to_id.get(tool_name)
                
                if output is not None and tool_call_id:
                    # Ensure output is properly formatted for the stream
                    logger.info(f"Processing tool output of type: {type(output)}")
                    logger.info(f"Tool output: {output}")
                    
                    output_data = output
                    if hasattr(output, 'content'):
                        # Handle the content based on its type
                        content = output.content
                        
                        if isinstance(content, list):
                            # Content is already a list
                            logger.info(f"Converting list content to dict: {len(content)} items")
                            output_data = {"items": content, "count": len(content)}
                        elif isinstance(content, dict):
                            # Content is already a dict
                            output_data = content
                        elif isinstance(content, str):
                            # Try to parse as JSON if it's a string
                            try:
                                parsed_content = json.loads(content)
                                if isinstance(parsed_content, list):
                                    logger.info(f"Converting parsed list to dict: {len(parsed_content)} items")
                                    output_data = {"items": parsed_content, "count": len(parsed_content)}
                                elif isinstance(parsed_content, dict):
                                    output_data = parsed_content
                                else:
                                    output_data = {"result": parsed_content}
                            except (json.JSONDecodeError, AttributeError):
                                output_data = {"result": content}
                        else:
                            output_data = {"result": str(content)}
                    elif isinstance(output, str):
                        # Try to parse as JSON
                        try:
                            parsed_output = json.loads(output)
                            if isinstance(parsed_output, list):
                                logger.info(f"Converting list output to dict: {len(parsed_output)} items")
                                output_data = {"items": parsed_output, "count": len(parsed_output)}
                            elif isinstance(parsed_output, dict):
                                output_data = parsed_output
                            else:
                                output_data = {"result": parsed_output}
                        except json.JSONDecodeError:
                            output_data = {"result": output}
                    elif isinstance(output, list):
                        # Handle list outputs (like empty issue lists)
                        logger.info(f"Converting list output to dict: {len(output)} items")
                        output_data = {"items": output, "count": len(output)}
                    elif not isinstance(output, dict):
                        # Convert to dict if it's not already
                        output_data = {"result": str(output)}
                    
                    logger.info(f"Final output_data type: {type(output_data)}")
                    
                    yield stream.tool_output_available(
                        tool_call_id=tool_call_id,
                        output_data=output_data
                    )
            
            # Handle any errors
            elif event_type == "on_chain_error":
                error_msg = f"Error in {name}: {data.get('error', 'Unknown error')}"
                logger.error(error_msg)
                yield stream.error(error_msg)
        
        logger.info("LangGraph astream_events completed")
        
    except Exception as e:
        logger.error(f"Error in LangGraph response generation: {str(e)}")
        yield stream.error(f"Error generating response: {str(e)}")
