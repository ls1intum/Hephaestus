// Mentor chat handler: persists thread, streams AI response, handles errors.

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

interface MessagePart {
	readonly type: string;
	readonly [key: string]: unknown;
}

function isMessagePartsArray(value: unknown): value is MessagePart[] {
	return (
		Array.isArray(value) &&
		value.every((item) => typeof item === "object" && item !== null && "type" in item)
	);
}

const IS_PRODUCTION = process.env.NODE_ENV === "production";

// biome-ignore lint/complexity/noExcessiveCognitiveComplexity: Handler orchestrates multiple concerns (persistence, streaming, error handling) which inherently adds complexity. Refactoring would split core chat logic across many files, reducing cohesion.
export const mentorChatHandler: AppRouteHandler<HandleMentorChatRoute> = async (c) => {
	const logger = getLogger(c);
	const requestBody = c.req.valid("json");
	const { id: threadId, message, previousMessageId, greeting } = requestBody;

	const workspaceId = c.get("workspaceId");
	const userId = c.get("userId");
	const userLogin = c.get("userLogin");
	const userName = c.get("userName");

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

	const threadResult = await loadOrCreateThread(
		threadId,
		workspaceId,
		userId,
		message ?? null,
		logger,
	);

	if (!threadResult.success && threadResult.error?.includes("access denied")) {
		return c.json({ error: ERROR_MESSAGES.THREAD_NOT_FOUND }, HTTP_STATUS.NOT_FOUND);
	}

	const thread = threadResult.success ? threadResult.data : null;

	if (threadResult.success && message) {
		await persistUserMessage(threadId, message, previousMessageId, logger);
	}

	const history = await getMessagesByThreadId(threadId);
	const historyMessages: UMessage[] = history.map(uiMessageFromPersisted);

	let modelMessages: ModelMessage[];
	if (isGreetingMode) {
		modelMessages = await convertToModelMessages(historyMessages);
	} else if (message) {
		const currentMessage = incomingToUiMessage(message);
		modelMessages = await convertToModelMessages([...historyMessages, currentMessage]);
	} else {
		return c.json({ error: "Message required unless greeting=true" }, HTTP_STATUS.BAD_REQUEST);
	}

	// userName is already first-name (MentorProxyController extracts it).
	const firstName = userName || userLogin;
	const isReturningUser = history.length > 0 || thread?.workspaceId !== undefined;
	const isFirstMessage = history.length === 0;

	const resolvedMainPrompt = loadPrompt(mentorChatPrompt);
	const resolvedGreetingFirst = loadPrompt(greetingFirstMessagePrompt);
	const resolvedGreetingContinue = loadPrompt(greetingContinuePrompt);
	const resolvedReturning = loadPrompt(returningUserPrompt);

	const greetingSection = String(
		isFirstMessage
			? resolvedGreetingFirst.compile({ firstName })
			: resolvedGreetingContinue.compile({}),
	);
	const returningUserSection = isReturningUser ? String(resolvedReturning.compile({})) : "";

	const compiledPrompt = String(
		resolvedMainPrompt.compile({ firstName, userLogin, greetingSection, returningUserSection }),
	);

	// Fall back to the in-code prompt if template compilation yielded nothing (defensive).
	const finalSystemPrompt =
		compiledPrompt && compiledPrompt.trim().length > 0
			? compiledPrompt
			: createMentorSystemPrompt({
					userLogin,
					userName: firstName,
					isReturningUser,
					messageCount: history.length,
				});

	// Pre-generate UUID so client + server agree on the assistant message ID.
	const assistantMessageId = uuidv4();
	const handleError = createAIErrorHandler(logger);

	logger.debug(
		{ threadId, assistantMessageId },
		"Generated UUID for assistant message - will be sent to client via stream",
	);

	// Stream AI response
	const stream = createUIMessageStream({
		// biome-ignore lint/complexity/noExcessiveCognitiveComplexity: Streaming handler coordinates multiple async operations (persistence, error handling) in a single callback. Splitting would fragment the streaming lifecycle logic.
		execute: async ({ writer }) => {
			try {
				writer.write({ type: "start", messageId: assistantMessageId });

				const greetingPrompt = `Greet ${firstName} warmly by name and ask what's on their mind. Keep it short (1-2 sentences).`;

				const model =
					typeof resolvedMainPrompt.config.model === "string"
						? getModel(resolvedMainPrompt.config.model)
						: env.defaultModel;

				const temperature =
					typeof resolvedMainPrompt.config.temperature === "number"
						? resolvedMainPrompt.config.temperature
						: undefined;

				const toolConfig = extractToolConfig(resolvedMainPrompt.config);

				const localTools = {
					createDocument: createDocumentTool({ dataStream: writer, workspaceId, userId }),
					updateDocument: updateDocumentTool({ dataStream: writer }),
					...createActivityTools(toolContext),
				};

				// Prompt config can override tool descriptions; local descriptions are the fallback.
				const overrideTools = getToolsFromConfig(resolvedMainPrompt.config);
				const tools = overrideToolDescriptions(
					localTools,
					overrideTools.length > 0 ? overrideTools : undefined,
				);

				const result = streamText({
					model,
					system: finalSystemPrompt,
					...(isGreetingMode ? { prompt: greetingPrompt } : { messages: modelMessages }),
					...(temperature !== undefined && { temperature }),
					tools,
					toolChoice: toolConfig.toolChoice ?? "auto",
					// Stop on max steps OR after document creation (terminal).
					stopWhen: [stepCountIs(toolConfig.maxToolSteps ?? 5), hasToolCall("createDocument")],
					// Disable one-shot tools after first use to keep prompt lean.
					prepareStep: ({ steps: previousSteps }) => {
						const oneShotTools = ["getActivitySummary", "getAssignedWork"];
						const usedTools = new Set(
							previousSteps.flatMap((step: { toolCalls: Array<{ toolName: string }> }) =>
								step.toolCalls.map((tc: { toolName: string }) => tc.toolName),
							),
						);
						if (!oneShotTools.some((t) => usedTools.has(t))) {
							return undefined;
						}
						const activeTools = Object.keys(tools).filter(
							(toolName) => !(oneShotTools.includes(toolName) && usedTools.has(toolName)),
						) as Array<keyof typeof tools>;
						return { activeTools };
					},
					onStepFinish: (stepResult) => {
						const { finishReason: stepFinishReason, usage, toolCalls } = stepResult;
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
						// We sent the start chunk above with our pre-generated UUID; suppress the default.
						sendStart: false,
						messageMetadata: ({ part }) => {
							if (part.type === "start") {
								return {
									createdAt: Date.now(),
									model: resolvedMainPrompt.config.model ?? env.MODEL_NAME,
								};
							}
							if (part.type === "finish") {
								return {
									inputTokens: part.totalUsage.inputTokens,
									outputTokens: part.totalUsage.outputTokens,
									totalTokens: part.totalUsage.totalTokens,
									finishReason: part.finishReason,
								};
							}
							return undefined;
						},
						onFinish: async ({ responseMessage, finishReason: streamFinishReason }) => {
							if (!threadResult.success) {
								logger.debug({ threadId }, "Skipping assistant message persistence - no thread");
								return;
							}

							const parts = isMessagePartsArray(responseMessage?.parts)
								? responseMessage.parts
								: [];

							// Use our pre-generated UUID, not responseMessage.id (must match the start chunk).
							await persistAssistantMessage(
								threadId,
								assistantMessageId,
								parts,
								message?.id ?? "",
								logger,
							);
							if (message) {
								await updateTitleIfNeeded(threadId, thread?.title, message, logger);
							}

							logger.debug(
								{ threadId, assistantMessageId, finishReason: streamFinishReason },
								"Assistant message persisted",
							);
						},
						onError: (error) => {
							const errorResult = handleError(error);
							logger.error(
								{
									category: errorResult.category,
									isRetryable: errorResult.isRetryable,
									details: errorResult.details,
								},
								`Streaming error: ${errorResult.userMessage}`,
							);
							return getStreamErrorMessage(error, IS_PRODUCTION);
						},
					}),
				);

				await result.consumeStream();
				const [totalUsage, steps, finishReason] = await Promise.all([
					result.totalUsage,
					result.steps,
					result.finishReason,
				]);

				logger.info(
					{
						threadId,
						assistantMessageId,
						isGreetingMode,
						finishReason,
						stepCount: steps.length,
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
				const errorResult = handleError(err);
				logger.error(
					{
						category: errorResult.category,
						isRetryable: errorResult.isRetryable,
						details: errorResult.details,
					},
					`Stream execution failed: ${errorResult.userMessage}`,
				);
				writer.write({ type: "error", errorText: errorResult.userMessage });
			}
		},
		onError: (e) => getStreamErrorMessage(e, IS_PRODUCTION),
	});

	return createUIMessageStreamResponse({ stream });
};
