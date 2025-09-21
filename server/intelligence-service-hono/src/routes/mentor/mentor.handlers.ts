import { trace } from "@opentelemetry/api";
import type { UIMessage as AIUIMessage } from "ai";
import {
  convertToCoreMessages,
  createUIMessageStream,
  createUIMessageStreamResponse,
  streamText,
} from "ai";

import env from "@/env";
import { buildTelemetryOptions } from "@/lib/telemetry";
import type { AppRouteHandler } from "@/lib/types";
import { mentorSystemPrompt } from "@/lib/ai/prompts";
import { getWeather } from "@/lib/ai/tools/get-weather";
import type { HandleMentorChatRoute } from "./mentor.routes";

import { ChatMessage, ChatRequestBody, chatRequestBodySchema } from "./mentor.schemas";

export const mentorChatHandler: AppRouteHandler<HandleMentorChatRoute> = async (
  context
) => {
  const logger = context.get("logger");
  const request = context.req.valid("json");
	
	let requestBody: ChatRequestBody;
	try {
		requestBody = chatRequestBodySchema.parse(request);
	} catch (_) {
		return context.json(
			{ error: "Invalid request body" },
			{ status: 400 },
		);
	}

	const { id, message }: { id: string; message: ChatMessage; } = requestBody;

	// TODO: const chat = await getChatById({ id });


  const coreMessages = convertToCoreMessages(originalMessages);
  const hasSystem = coreMessages.some((msg) => msg.role === "system");
  if (!hasSystem) {
    coreMessages.unshift({ role: "system", content: systemPrompt });
  }

  if (validated.user_id && coreMessages.length === 1) {
    coreMessages.push({
      role: "user",
      content: `User ID: ${validated.user_id}. Please introduce yourself and offer to help with their development work.`,
    });
  }

  const summary = summarizeMessages(validated.messages);
  logger.info(
    {
      userId: validated.user_id,
      messages: summary.count,
      firstRole: summary.firstRole,
      lastRole: summary.lastRole,
      firstText: summary.firstText?.slice(0, 80),
      lastText: summary.lastText?.slice(0, 80),
    },
    "Processing mentor chat request"
  );

  updateActiveObservation({
    input: {
      user_id: validated.user_id,
      message_count: summary.count,
    },
  });

  updateActiveTrace({
    name: "mentor:chat",
    input: {
      user_id: validated.user_id,
      messages: coreMessages,
    },
  });

  const stream = createUIMessageStream({
    originalMessages,
    async execute({ writer }) {
      const toolContext: MentorToolContext = {
        writer,
        userId: validated.user_id,
        logger,
        request: validated,
      };

      const tools = createMentorTools(toolContext);

      const result = streamText({
        model: env.defaultModel,
        messages: coreMessages,
        tools,
        toolChoice: "auto",
        experimental_context: toolContext,
        ...buildTelemetryOptions(promptTemplate),
      });

      writer.merge(
        result.toUIMessageStream({
          originalMessages,
          onFinish: ({ responseMessage }) => {
            updateActiveObservation({ output: responseMessage });
            updateActiveTrace({
              output: {
                message: responseMessage,
                trace_id: getActiveTraceId() ?? "",
              },
            });
            trace.getActiveSpan()?.end();
          },
          onError: (error) => {
            logger.error({ err: error }, "Mentor response streaming error");
            return error instanceof Error
              ? error.message
              : "An unexpected error occurred.";
          },
        })
      );

      try {
        await result.text;
      } catch (error) {
        logger.error({ err: error }, "Mentor model invocation failed");
        throw error;
      }
    },
    onError: (error): string => {
      logger.error({ err: error }, "Mentor chat stream error");
      const message =
        error instanceof Error
          ? error.message
          : "An unexpected error occurred.";
      updateActiveObservation({ output: { error: message } });
      updateActiveTrace({
        output: {
          error: message,
          trace_id: getActiveTraceId() ?? "",
        },
      });
      trace.getActiveSpan()?.end();
      return message;
    },
  });

  const response = createUIMessageStreamResponse({ stream });
  return response;
};
