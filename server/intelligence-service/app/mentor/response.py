import inspect
import json
from langchain_core.messages import SystemMessage, AIMessageChunk, ToolMessage

from app.mentor.streaming import StreamGenerator
from app.mentor.tools import get_weather
from app.models import get_model
from app.settings import settings
from app.logger import logger


ChatModel = get_model(settings.MODEL_NAME)
model = ChatModel(temperature=0.7, max_tokens=4096)


system_prompt = """\
You are a friendly assistant! Keep your responses concise and helpful.

About the origin of user's request:
- lat: 48.137154
- lon: 11.576124
- city: Munich
- country: Germany

When you need to call tools, always explain to the user what you're about to do before making the tool call. For example, before calling the weather tool, say something like "Let me check the current weather for your location in Munich."

After receiving tool results, provide a clear and helpful interpretation of the data to the user.
"""


async def generate_response(messages, stream: StreamGenerator):
    """Generate a response using the language model with proper tool calling loop."""
    # Available tools
    tools = [get_weather]
    model_with_tools = model.bind_tools(tools)

    # Create tool mapping for execution
    tool_map = {tool.name: tool for tool in tools}

    # Prepare messages with system prompt
    working_messages = [SystemMessage(content=system_prompt)] + messages

    try:
        # Main conversation loop - handles multiple tool calling rounds
        max_iterations = 5  # Prevent infinite loops
        iteration = 0

        while iteration < max_iterations:
            iteration += 1
            logger.debug(f"Starting conversation iteration {iteration}")

            # Stream the model response
            gathered_response = AIMessageChunk(content="")
            tool_calls_to_execute = []

            logger.info(f"Sending {len(working_messages)} messages to model")
            for i, msg in enumerate(working_messages):
                logger.debug(
                    f"Message {i}: {type(msg).__name__} - {msg.content[:100]}..."
                )

            async for chunk in model_with_tools.astream(working_messages):
                chunk = AIMessageChunk.model_validate(chunk)

                # Handle tool calls
                if chunk.tool_calls:
                    # On first tool call chunk, start streaming tool input
                    if not gathered_response.tool_calls:
                        logger.info(
                            f"Starting tool calls: {[tc['name'] for tc in chunk.tool_calls]}"
                        )
                        for tool_call in chunk.tool_calls:
                            yield stream.tool_input_start(
                                tool_call_id=tool_call["id"],
                                tool_name=tool_call["name"],
                            )

                    # Stream tool input deltas more intelligently
                    for i, tool_call in enumerate(chunk.tool_calls):
                        if i < len(gathered_response.tool_calls):
                            # Continuing existing tool call - stream argument updates
                            existing_args = gathered_response.tool_calls[i].get(
                                "args", {}
                            )
                            new_args = tool_call.get("args", {})

                            # Only stream meaningful deltas
                            if new_args and new_args != existing_args:
                                # Convert to string for delta streaming
                                try:
                                    delta_str = json.dumps(
                                        new_args, separators=(",", ":")
                                    )
                                    yield stream.tool_input_delta(
                                        tool_call_id=tool_call["id"],
                                        input_text_delta=delta_str,
                                    )
                                except (TypeError, ValueError) as e:
                                    logger.warning(
                                        f"Could not serialize tool args for streaming: {e}"
                                    )
                        else:
                            # New tool call
                            logger.info(f"New tool call detected: {tool_call['name']}")
                            yield stream.tool_input_start(
                                tool_call_id=tool_call["id"],
                                tool_name=tool_call["name"],
                            )

                # Handle regular content
                if chunk.content:
                    yield stream.text(chunk.content)

                # Accumulate the response
                gathered_response += chunk

            # Process completed tool calls
            if gathered_response.tool_calls:
                logger.info(
                    f"Processing {len(gathered_response.tool_calls)} tool calls"
                )

                # CRITICAL: Add the assistant's tool-calling message FIRST
                working_messages.append(gathered_response)

                for tool_call in gathered_response.tool_calls:
                    tool_name = tool_call["name"]
                    tool_call_id = tool_call["id"]
                    tool_args = tool_call["args"]

                    # Signal tool input is available
                    yield stream.tool_input_available(
                        tool_call_id=tool_call_id,
                        tool_name=tool_name,
                        input_data=tool_args,
                    )

                    try:
                        # Execute the tool
                        if tool_name in tool_map:
                            logger.info(
                                f"Executing tool: {tool_name} with args: {tool_args}"
                            )

                            # Execute tool function
                            tool_func = tool_map[tool_name]
                            if inspect.iscoroutinefunction(tool_func.func):
                                tool_result = await tool_func.func(**tool_args)
                            else:
                                tool_result = tool_func.func(**tool_args)

                            logger.info(f"Tool {tool_name} result: {tool_result}")

                            # Stream tool output
                            yield stream.tool_output_available(
                                tool_call_id=tool_call_id, output_data=tool_result
                            )

                            # Add tool message to conversation AFTER the AIMessage
                            tool_message = ToolMessage(
                                content=json.dumps(tool_result),
                                tool_call_id=tool_call_id,
                            )
                            working_messages.append(tool_message)

                        else:
                            error_msg = f"Unknown tool: {tool_name}"
                            logger.error(error_msg)
                            yield stream.error(error_msg)

                            # Add error tool message
                            tool_message = ToolMessage(
                                content=f"Error: {error_msg}", tool_call_id=tool_call_id
                            )
                            working_messages.append(tool_message)

                    except Exception as e:
                        error_msg = f"Error executing tool {tool_name}: {str(e)}"
                        logger.error(error_msg)
                        yield stream.error(error_msg)

                        # Add error tool message
                        tool_message = ToolMessage(
                            content=f"Error: {error_msg}", tool_call_id=tool_call_id
                        )
                        working_messages.append(tool_message)

                # Continue the loop to let the model respond to tool results
                logger.info("Tool execution complete, continuing conversation...")
                continue

            else:
                # No tool calls, conversation is complete
                logger.info("No tool calls in response, conversation complete")

                # Add final assistant message to working messages
                if gathered_response.content:
                    working_messages.append(gathered_response)
                break

    except Exception as e:
        logger.error(f"Error in model response generation: {str(e)}")
        yield stream.error(f"Error generating response: {str(e)}")
