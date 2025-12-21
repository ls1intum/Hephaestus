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
	hasToolCall,
	stepCountIs,
	streamText,
} from "ai";
import { v4 as uuidv4 } from "uuid";
import env from "@/env";
import {
	greetingContinuePrompt,
	greetingFirstMessagePrompt,
	mentorChatPrompt,
	returningUserPrompt,
} from "@/mentor/chat.prompt";
import { createActivityTools, extractToolConfig, overrideToolDescriptions } from "@/mentor/tools";
import { createDocumentTool } from "@/mentor/tools/document-create.tool";
import { updateDocumentTool } from "@/mentor/tools/document-update.tool";
import { loadPrompt } from "@/prompts";
import { getToolsFromConfig } from "@/prompts/types";
import { createAIErrorHandler, getStreamErrorMessage } from "@/shared/ai/error-handler";
import { getModel } from "@/shared/ai/model";
import { createMentorSystemPrompt } from "@/shared/ai/prompts";
import { buildTelemetryOptions } from "@/shared/ai/telemetry";
import { ERROR_MESSAGES, HTTP_STATUS } from "@/shared/constants";
import type { AppRouteHandler } from "@/shared/http/types";
import { getLogger } from "@/shared/utils";
import {
	loadOrCreateThread,
	persistAssistantMessage,
	persistUserMessage,
	updateTitleIfNeeded,
} from "./chat.persistence";
import type { HandleMentorChatRoute } from "./chat.routes";
import { incomingToUiMessage, type UMessage, uiMessageFromPersisted } from "./chat.transformer";
import { getMessagesByThreadId } from "./data";

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

/** Message part from AI SDK response (minimal shape for persistence) */
interface MessagePart {
	readonly type: string;
	readonly [key: string]: unknown;
}

/** Type guard for message parts array */
function isMessagePartsArray(value: unknown): value is MessagePart[] {
	return (
		Array.isArray(value) &&
		value.every((item) => typeof item === "object" && item !== null && "type" in item)
	);
}

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
export const mentorChatHandler: AppRouteHandler<HandleMentorChatRoute> = async (c) => {
	const logger = getLogger(c);
	// Hono's c.req.valid() returns the validated type from the route schema
	const requestBody = c.req.valid("json");
	const { id: threadId, message, previousMessageId, greeting } = requestBody;

	const workspaceId = c.get("workspaceId");
	const userId = c.get("userId");
	const userLogin = c.get("userLogin");
	const userName = c.get("userName"); // Real display name if available

	// Greeting mode: generate initial greeting without user message
	const isGreetingMode = greeting === true && !message;

	logger.info(
		{ workspaceId, userId, userLogin, userName, threadId, isGreetingMode },
		"Chat request received",
	);

	// Validate required context for tools - fail fast if missing
	if (!(workspaceId && userId && userLogin)) {
		logger.error({ workspaceId, userId, userLogin }, "Missing required context headers");
		return c.json(
			{ error: "Missing required context (workspaceId, userId, or userLogin)" },
			HTTP_STATUS.BAD_REQUEST,
		);
	}

	// Create tool context - this is automatically injected into all tools
	// so the model NEVER needs to ask for user information
	const toolContext = { workspaceId, userId, userLogin, userName: userName || userLogin };

	// Persistence (best-effort - degradation allowed but logged)
	// Thread ownership is verified here - returns error if thread exists but belongs to another user
	const threadResult = await loadOrCreateThread(
		threadId,
		workspaceId,
		userId,
		message ?? null,
		logger,
	);

	// If thread exists but belongs to another user, deny access
	if (!threadResult.success && threadResult.error?.includes("access denied")) {
		return c.json({ error: ERROR_MESSAGES.THREAD_NOT_FOUND }, HTTP_STATUS.NOT_FOUND);
	}

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
		return c.json({ error: "Message required unless greeting=true" }, HTTP_STATUS.BAD_REQUEST);
	}

	// Create personalized system prompt with user context
	// userName is already the first name (extracted by MentorProxyController)
	const firstName = userName || userLogin;
	const isReturningUser = history.length > 0 || thread?.workspaceId !== undefined;
	const isFirstMessage = history.length === 0;

	// Load all prompts from Langfuse (with local fallback)
	// Sub-prompts are loaded so they can be versioned/tested independently
	// LIMITATION: Only the main prompt can be linked to the trace (Langfuse constraint)
	// But the actual prompt text IS captured in the trace input
	// All mentor prompts are text prompts (type: "text"), so compile() returns string
	const [resolvedMainPrompt, resolvedGreetingFirst, resolvedGreetingContinue, resolvedReturning] =
		await Promise.all([
			loadPrompt(mentorChatPrompt, { label: "production" }),
			loadPrompt(greetingFirstMessagePrompt, { label: "production" }),
			loadPrompt(greetingContinuePrompt, { label: "production" }),
			loadPrompt(returningUserPrompt, { label: "production" }),
		]);

	// Select greeting section based on conversation state
	// The sub-prompt's .prompt is injected as a variable value
	// Type assertion is safe: all prompts are text type (verified at definition)
	const greetingSection = String(
		isFirstMessage
			? resolvedGreetingFirst.compile({ firstName })
			: resolvedGreetingContinue.compile({}),
	);

	// Select returning user section (also a text prompt)
	const returningUserSection = isReturningUser ? String(resolvedReturning.compile({})) : "";

	// Compose the final system prompt
	// The main prompt uses {{greetingSection}} and {{returningUserSection}} as variables
	const compiledPrompt = String(
		resolvedMainPrompt.compile({
			firstName,
			userLogin,
			greetingSection,
			returningUserSection,
		}),
	);

	// Fallback: use local prompt generator if Langfuse prompt compilation fails
	const finalSystemPrompt =
		compiledPrompt && compiledPrompt.trim().length > 0
			? compiledPrompt
			: createMentorSystemPrompt({
					userLogin,
					userName: firstName,
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
			promptSource: resolvedMainPrompt.source,
			promptVersion: resolvedMainPrompt.langfuseVersion,
			subPromptVersions: {
				greeting: isFirstMessage
					? resolvedGreetingFirst.langfuseVersion
					: resolvedGreetingContinue.langfuseVersion,
				returning: isReturningUser ? resolvedReturning.langfuseVersion : null,
			},
		},
		"Generated UUID for assistant message - will be sent to client via stream",
	);

	// Determine trace name based on mode
	const traceName = isGreetingMode ? TRACE_NAME_GREETING : TRACE_NAME_CHAT;

	// Stream AI response
	const stream = createUIMessageStream({
		// biome-ignore lint/complexity/noExcessiveCognitiveComplexity: Streaming handler coordinates multiple async operations (persistence, telemetry, error handling) in a single callback. Splitting would fragment the streaming lifecycle logic.
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
				// IMPORTANT: Only the main prompt is linked - Langfuse limitation
				// Sub-prompt versions are logged in metadata for debugging
				const telemetryOptions = buildTelemetryOptions(resolvedMainPrompt, traceName, {
					sessionId: threadId, // Group conversation messages
					userId: userLogin,
					workspaceId: String(workspaceId),
					messageCount: String(modelMessages.length),
					isReturningUser: String(isReturningUser),
					isFirstMessage: String(isFirstMessage),
					// Log sub-prompt versions for debugging (not linked, just metadata)
					greetingPromptVersion: String(
						isFirstMessage
							? (resolvedGreetingFirst.langfuseVersion ?? "local")
							: (resolvedGreetingContinue.langfuseVersion ?? "local"),
					),
				});

				// Resolve model from Langfuse config or fall back to env default
				// This follows Langfuse v4 best practice: store model in prompt config
				// @see https://langfuse.com/docs/prompt-management/features/config#using-the-config
				const model =
					typeof resolvedMainPrompt.config.model === "string"
						? getModel(resolvedMainPrompt.config.model)
						: env.defaultModel;

				// Resolve temperature from Langfuse config (optional)
				const temperature =
					typeof resolvedMainPrompt.config.temperature === "number"
						? resolvedMainPrompt.config.temperature
						: undefined;

				// Extract tool config from Langfuse (toolChoice, maxToolSteps)
				const toolConfig = extractToolConfig(resolvedMainPrompt.config);

				// Create local tools with fallback descriptions
				const localTools = {
					// Document tools (require stream writer)
					createDocument: createDocumentTool({ dataStream: writer, workspaceId, userId }),
					updateDocument: updateDocumentTool({ dataStream: writer }),
					// Activity tools via registry - all parallel-safe, user context auto-injected
					...createActivityTools(toolContext),
				};

				// Override tool descriptions with Langfuse-managed versions
				// This allows A/B testing tool descriptions and updating without code deployment
				// @see https://langfuse.com/docs/prompt-management/features/config#function-calling
				const langfuseTools = getToolsFromConfig(resolvedMainPrompt.config);
				const tools = overrideToolDescriptions(
					localTools,
					langfuseTools.length > 0 ? langfuseTools : undefined,
				);

				const result = streamText({
					model,
					system: finalSystemPrompt,
					// Use prompt for greeting, messages for normal chat
					...(isGreetingMode ? { prompt: greetingPrompt } : { messages: modelMessages }),
					// Apply temperature from Langfuse config if available
					...(temperature !== undefined && { temperature }),
					// Tools with Langfuse-managed descriptions
					tools,
					// Tool choice from Langfuse config (default: auto)
					toolChoice: toolConfig.toolChoice ?? "auto",
					// Allow multi-step tool calling: the model can call tools and then
					// continue generating a response based on tool results.
					// Stops on: max steps OR after document creation (terminal action)
					stopWhen: [stepCountIs(toolConfig.maxToolSteps ?? 5), hasToolCall("createDocument")],
					// Dynamic tool activation: disable one-shot tools after they've been called
					// This reduces token usage by removing irrelevant tool descriptions
					prepareStep: ({ steps: previousSteps }) => {
						// Tools that provide static data - no need to call more than once
						const oneShotTools = ["getActivitySummary", "getAssignedWork"];
						const usedTools = new Set(
							previousSteps.flatMap((step: { toolCalls: Array<{ toolName: string }> }) =>
								step.toolCalls.map((tc: { toolName: string }) => tc.toolName),
							),
						);

						// Check if any one-shot tool was already used
						const hasUsedOneShotTool = oneShotTools.some((t) => usedTools.has(t));
						if (!hasUsedOneShotTool) {
							return undefined; // No changes needed
						}

						// Filter out already-used one-shot tools
						const activeTools = Object.keys(tools).filter(
							(toolName) => !(oneShotTools.includes(toolName) && usedTools.has(toolName)),
						) as Array<keyof typeof tools>;

						return { activeTools };
					},
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

							const parts = isMessagePartsArray(responseMessage?.parts)
								? responseMessage.parts
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
						promptSource: resolvedMainPrompt.source,
						promptVersion: resolvedMainPrompt.langfuseVersion,
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
