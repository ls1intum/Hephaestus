/**
 * Chat handler for AI mentor interactions.
 *
 * This handler orchestrates:
 * 1. Thread/message persistence
 * 2. AI model invocation with tool calling
 * 3. Response streaming to client
 * 4. Error handling with AI SDK type discrimination
 * 5. Usage metrics logging for telemetry
 * 6. Langfuse observability with proper trace naming
 *
 * Complex logic is delegated to:
 * - chat.transformer.ts: Type conversions
 * - chat.persistence.ts: Database operations
 * - error-handler.ts: AI SDK error handling
 */

import type { ModelMessage } from "ai";
import {
	convertToModelMessages,
	createUIMessageStream,
	createUIMessageStreamResponse,
	stepCountIs,
	streamText,
} from "ai";
import { v4 as uuidv4 } from "uuid";
import env from "@/env";
import { createActivityTools } from "@/mentor/tools";
import { createDocument as createDocumentFactory } from "@/mentor/tools/document-create.tool";
import { updateDocument as updateDocumentFactory } from "@/mentor/tools/document-update.tool";
import { loadPrompt, mentorChatPrompt } from "@/prompts";
import { createAIErrorHandler, getStreamErrorMessage } from "@/shared/ai/error-handler";
import { createMentorSystemPrompt } from "@/shared/ai/prompts";
import { buildTelemetryOptions } from "@/shared/ai/telemetry";
import { ERROR_MESSAGES, HTTP_STATUS } from "@/shared/constants";
import type { AppRouteHandler } from "@/shared/http/types";
import { extractErrorMessage, getLogger } from "@/shared/utils";
import {
	loadOrCreateThread,
	persistAssistantMessage,
	persistUserMessage,
	updateTitleIfNeeded,
} from "./chat.persistence";
import type { HandleGetThreadRoute, HandleMentorChatRoute } from "./chat.routes";
import type { ThreadDetail } from "./chat.schema";
import {
	incomingToUiMessage,
	toThreadDetailMessage,
	type UMessage,
	uiMessageFromPersisted,
} from "./chat.transformer";
import { getMessagesByThreadId } from "./data";

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

const IS_PRODUCTION = process.env.NODE_ENV === "production";
const TRACE_NAME_CHAT = "mentor:chat";
const TRACE_NAME_GREETING = "mentor:greeting";

// ─────────────────────────────────────────────────────────────────────────────
// Chat Handler
// ─────────────────────────────────────────────────────────────────────────────

// biome-ignore lint/complexity/noExcessiveCognitiveComplexity: Handler orchestrates multiple concerns (persistence, streaming, telemetry, error handling) which inherently adds complexity. Refactoring would split core chat logic across many files, reducing cohesion.
export const mentorChatHandler: AppRouteHandler<HandleMentorChatRoute> = async (context) => {
	const logger = getLogger(context);
	// Hono's c.req.valid() returns the validated type from the route schema
	const requestBody = context.req.valid("json");
	const { id: threadId, message, previousMessageId, greeting } = requestBody;

	const workspaceId = context.get("workspaceId");
	const userId = context.get("userId");
	const userLogin = context.get("userLogin");
	const userName = context.get("userName"); // Real display name if available

	// Greeting mode: generate initial greeting without user message
	const isGreetingMode = greeting === true && !message;

	logger.info(
		{ workspaceId, userId, userLogin, userName, threadId, isGreetingMode },
		"Chat request received",
	);

	// Validate required context for tools - fail fast if missing
	if (!(workspaceId && userId && userLogin)) {
		logger.error({ workspaceId, userId, userLogin }, "Missing required context headers");
		return context.json(
			{ error: "Missing required context (workspaceId, userId, or userLogin)" },
			HTTP_STATUS.BAD_REQUEST,
		);
	}

	// Create tool context - this is automatically injected into all tools
	// so the model NEVER needs to ask for user information
	const toolContext = { workspaceId, userId, userLogin, userName: userName || userLogin };

	// Persistence (best-effort - degradation allowed but logged)
	const threadResult = await loadOrCreateThread(threadId, workspaceId, message ?? null, logger);
	const thread = threadResult.success ? threadResult.data : null;

	// Only persist user message if we have one and thread exists
	if (threadResult.success && message) {
		await persistUserMessage(threadId, message, previousMessageId, logger);
	}

	// Build conversation history
	const history = await getMessagesByThreadId(threadId);
	const historyMessages: UMessage[] = history.map(uiMessageFromPersisted);

	// Build model messages: include user message only if not in greeting mode
	let modelMessages: ModelMessage[];
	if (isGreetingMode) {
		// Greeting mode: no user message, just history (usually empty)
		modelMessages = await convertToModelMessages(historyMessages);
	} else if (message) {
		const currentMessage = incomingToUiMessage(message);
		const uiMessages: UMessage[] = [...historyMessages, currentMessage];
		modelMessages = await convertToModelMessages(uiMessages);
	} else {
		// No message and not greeting - bad request
		return context.json(
			{ error: "Message required unless greeting=true" },
			HTTP_STATUS.BAD_REQUEST,
		);
	}

	// Create personalized system prompt with user context
	// Use real name if available, fall back to login
	const displayName = userName || userLogin;
	const firstName = displayName.split(" ")[0] || userLogin;
	const isReturningUser = history.length > 0 || thread?.workspaceId !== undefined;
	const isFirstMessage = history.length === 0;

	// Load mentor system prompt from Langfuse (with local fallback)
	// mentorChatPrompt is type "text", so compile returns string
	const resolvedPrompt = await loadPrompt(mentorChatPrompt, { label: "production" });
	const compiledPrompt = resolvedPrompt.compile({
		firstName,
		userLogin,
		isFirstMessage: String(isFirstMessage),
		isReturningUser: String(isReturningUser),
	}) as string;

	// Fallback: use local prompt generator if Langfuse prompt compilation fails
	// (e.g., if template variables don't match)
	const finalSystemPrompt =
		compiledPrompt && compiledPrompt.trim().length > 0
			? compiledPrompt
			: createMentorSystemPrompt({
					userLogin,
					userName: displayName,
					isReturningUser,
					messageCount: history.length,
				});

	// Generate a UUID for the assistant message BEFORE streaming starts
	// This ensures both client and server use the same ID for the message
	const assistantMessageId = uuidv4();

	// Create error handler for this request
	const handleError = createAIErrorHandler(logger);

	logger.debug(
		{
			threadId,
			assistantMessageId,
			promptSource: resolvedPrompt.source,
			promptVersion: resolvedPrompt.langfuseVersion,
		},
		"Generated UUID for assistant message - will be sent to client via stream",
	);

	// Determine trace name based on mode
	const traceName = isGreetingMode ? TRACE_NAME_GREETING : TRACE_NAME_CHAT;

	// Stream AI response
	const stream = createUIMessageStream({
		execute: async ({ writer }) => {
			try {
				// Write the start chunk with our server-generated UUID
				// This ID will be used by the client for the assistant message
				writer.write({
					type: "start",
					messageId: assistantMessageId,
				});

				// For greeting mode, use prompt instead of messages
				// This allows the model to generate a greeting without any user input
				const greetingPrompt = `Greet ${firstName} warmly by name and ask what's on their mind. Keep it short (1-2 sentences).`;

				// Build telemetry options with unified helper
				// Handles both Langfuse-sourced and local prompts uniformly
				const telemetryOptions = buildTelemetryOptions(resolvedPrompt, traceName, {
					sessionId: threadId, // Group conversation messages
					userId: userLogin,
					workspaceId: String(workspaceId),
					messageCount: String(modelMessages.length),
					isReturningUser: String(isReturningUser),
				});

				const result = streamText({
					model: env.defaultModel,
					system: finalSystemPrompt,
					// Use prompt for greeting, messages for normal chat
					...(isGreetingMode ? { prompt: greetingPrompt } : { messages: modelMessages }),
					// Create all tools via registry for consistent configuration
					tools: {
						// Document tools (require stream writer)
						createDocument: createDocumentFactory({ dataStream: writer, workspaceId, userId }),
						updateDocument: updateDocumentFactory({ dataStream: writer }),
						// Activity tools via registry - all parallel-safe, user context auto-injected
						...createActivityTools(toolContext),
					},
					toolChoice: "auto",
					// Allow multi-step tool calling: the model can call tools and then
					// continue generating a response based on tool results.
					// Max steps is configurable via Langfuse prompt config
					stopWhen: stepCountIs(
						typeof resolvedPrompt.config.maxToolSteps === "number"
							? resolvedPrompt.config.maxToolSteps
							: 5,
					),
					// Enable Langfuse telemetry for observability
					...telemetryOptions,
					// Callback for each step - useful for tracking tool usage
					onStepFinish: (stepResult) => {
						const { finishReason: stepFinishReason, usage, toolCalls } = stepResult;

						// Log step completion with usage metrics
						if (usage) {
							logger.debug(
								{
									threadId,
									finishReason: stepFinishReason,
									toolCalls: toolCalls?.map((tc) => tc.toolName),
									usage: {
										inputTokens: usage.inputTokens,
										outputTokens: usage.outputTokens,
										totalTokens: usage.totalTokens,
									},
								},
								"Step completed with token usage",
							);
						}
					},
				});

				writer.merge(
					result.toUIMessageStream({
						sendReasoning: true,
						// Omit the default start chunk since we already sent one with our UUID
						sendStart: false,
						onFinish: async ({ responseMessage, finishReason: streamFinishReason }) => {
							// Only persist if thread was successfully created
							if (!threadResult.success) {
								logger.debug({ threadId }, "Skipping assistant message persistence - no thread");
								return;
							}

							const parts = Array.isArray(responseMessage?.parts)
								? (responseMessage.parts as Array<{ type: string; [k: string]: unknown }>)
								: [];

							// Use our pre-generated UUID, NOT the responseMessage.id
							// This ensures the ID matches what the client received in the start chunk
							// For greeting mode, there's no parent message
							await persistAssistantMessage(
								threadId,
								assistantMessageId,
								parts,
								message?.id ?? "",
								logger,
							);
							// Only update title if we have a user message
							if (message) {
								await updateTitleIfNeeded(threadId, thread?.title, message, logger);
							}

							logger.debug(
								{ threadId, assistantMessageId, finishReason: streamFinishReason },
								"Assistant message persisted",
							);
						},
						onError: (error) => {
							// Use AI SDK error handler for proper type discrimination
							const errorResult = handleError(error);
							logger.error(
								{
									category: errorResult.category,
									isRetryable: errorResult.isRetryable,
									details: errorResult.details,
								},
								`Streaming error: ${errorResult.userMessage}`,
							);
							// Return user-friendly message for stream
							return getStreamErrorMessage(error, IS_PRODUCTION);
						},
					}),
				);

				await result.consumeStream();

				// Log final usage metrics after stream is complete
				// These promises are resolved after consumeStream()
				const [totalUsage, steps, finishReason] = await Promise.all([
					result.totalUsage,
					result.steps,
					result.finishReason,
				]);

				logger.info(
					{
						threadId,
						assistantMessageId,
						traceName,
						isGreetingMode,
						finishReason,
						stepCount: steps.length,
						promptSource: resolvedPrompt.source,
						promptVersion: resolvedPrompt.langfuseVersion,
						usage: {
							inputTokens: totalUsage.inputTokens,
							outputTokens: totalUsage.outputTokens,
							totalTokens: totalUsage.totalTokens,
						},
						toolsUsed: steps.flatMap((step) => step.toolCalls.map((tc) => tc.toolName)),
					},
					"Chat response completed - usage metrics logged",
				);
			} catch (err) {
				// Handle errors that occur during stream setup
				const errorResult = handleError(err);
				logger.error(
					{
						category: errorResult.category,
						isRetryable: errorResult.isRetryable,
						details: errorResult.details,
					},
					`Stream execution failed: ${errorResult.userMessage}`,
				);

				// Write error to stream so client receives it instead of hanging
				writer.write({
					type: "error",
					errorText: errorResult.userMessage,
				});
			}
		},
		onError: (e) => {
			// Use stream-safe error message
			return getStreamErrorMessage(e, IS_PRODUCTION);
		},
	});

	return createUIMessageStreamResponse({ stream });
};

// ─────────────────────────────────────────────────────────────────────────────
// Get Thread Handler
// ─────────────────────────────────────────────────────────────────────────────

export const getThreadHandler: AppRouteHandler<HandleGetThreadRoute> = async (context) => {
	const logger = getLogger(context);
	const { threadId } = context.req.valid("param");

	try {
		const { getThreadById } = await import("./data");
		const thread = await getThreadById(threadId);

		if (!thread) {
			return context.json(
				{ error: ERROR_MESSAGES.THREAD_NOT_FOUND },
				{ status: HTTP_STATUS.NOT_FOUND },
			);
		}

		const history = await getMessagesByThreadId(threadId);
		const messages = history.map(toThreadDetailMessage);

		const response: ThreadDetail = {
			id: thread.id,
			title: thread.title ?? null,
			selectedLeafMessageId: thread.selectedLeafMessageId ?? null,
			messages,
		};

		return context.json(response, { status: HTTP_STATUS.OK });
	} catch (err) {
		logger.error({ err: extractErrorMessage(err) }, "Failed to fetch thread");
		return context.json(
			{ error: ERROR_MESSAGES.SERVICE_UNAVAILABLE },
			{ status: HTTP_STATUS.SERVICE_UNAVAILABLE },
		);
	}
};
