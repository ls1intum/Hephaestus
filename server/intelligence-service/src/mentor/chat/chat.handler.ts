/**
 * Chat handler for AI mentor interactions.
 *
 * This handler orchestrates:
 * 1. Thread/message persistence
 * 2. AI model invocation with tool calling
 * 3. Response streaming to client
 *
 * Complex logic is delegated to:
 * - chat.transformer.ts: Type conversions
 * - chat.persistence.ts: Database operations
 */

import {
	convertToModelMessages,
	createUIMessageStream,
	createUIMessageStreamResponse,
	streamText,
} from "ai";
import { v4 as uuidv4 } from "uuid";
import env from "@/env";
import { createDocument as createDocumentFactory } from "@/mentor/tools/document-create.tool";
import { updateDocument as updateDocumentFactory } from "@/mentor/tools/document-update.tool";
import { getIssues } from "@/mentor/tools/issue-list.tool";
import { getIssueDetails } from "@/mentor/tools/issues.tool";
import { getPullRequestDetails } from "@/mentor/tools/pull-request.tool";
import { getPullRequests } from "@/mentor/tools/pull-request-list.tool";
import { getPullRequestBadPractices } from "@/mentor/tools/pull-request-review.tool";
import { mentorSystemPrompt } from "@/shared/ai/prompts";
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

// ─────────────────────────────────────────────────────────────────────────────
// Chat Handler
// ─────────────────────────────────────────────────────────────────────────────

export const mentorChatHandler: AppRouteHandler<HandleMentorChatRoute> = async (context) => {
	const logger = getLogger(context);
	// Hono's c.req.valid() returns the validated type from the route schema
	const requestBody = context.req.valid("json");
	const { id: threadId, message, previousMessageId } = requestBody;

	const workspaceId = context.get("workspaceId");
	const userId = context.get("userId");

	logger.info({ workspaceId, userId, threadId }, "Chat request received");

	// Persistence (best-effort - degradation allowed but logged)
	const threadResult = await loadOrCreateThread(threadId, workspaceId, message, logger);
	const thread = threadResult.success ? threadResult.data : null;

	// Only persist user message if thread exists (to avoid FK violation cascade)
	if (threadResult.success) {
		await persistUserMessage(threadId, message, previousMessageId, logger);
	}

	// Build conversation history
	const history = await getMessagesByThreadId(threadId);
	const historyMessages: UMessage[] = history.map(uiMessageFromPersisted);
	const currentMessage = incomingToUiMessage(message);
	const uiMessages: UMessage[] = [...historyMessages, currentMessage];
	const modelMessages = convertToModelMessages(uiMessages);

	// Generate a UUID for the assistant message BEFORE streaming starts
	// This ensures both client and server use the same ID for the message
	const assistantMessageId = uuidv4();

	logger.debug(
		{ threadId, assistantMessageId },
		"Generated UUID for assistant message - will be sent to client via stream",
	);

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

				const result = streamText({
					model: env.defaultModel,
					system: mentorSystemPrompt,
					messages: modelMessages,
					tools: {
						createDocument: createDocumentFactory({ dataStream: writer, workspaceId, userId }),
						updateDocument: updateDocumentFactory({ dataStream: writer }),
						getIssues,
						getPullRequests,
						getIssueDetails,
						getPullRequestDetails,
						getPullRequestBadPractices,
					},
					toolChoice: "auto",
				});

				writer.merge(
					result.toUIMessageStream({
						sendReasoning: true,
						// Omit the default start chunk since we already sent one with our UUID
						sendStart: false,
						onFinish: async ({ responseMessage }) => {
							// Only persist if thread was successfully created
							if (!threadResult.success) {
								logger.debug({ threadId }, "Skipping assistant message persistence - no thread");
								return;
							}

							// Log the response message ID for debugging
							logger.debug(
								{
									threadId,
									assistantMessageId,
									responseMessageId: responseMessage?.id,
								},
								"Assistant message stream finished - using server-generated UUID",
							);

							const parts = Array.isArray(responseMessage?.parts)
								? (responseMessage.parts as Array<{ type: string; [k: string]: unknown }>)
								: [];

							// Use our pre-generated UUID, NOT the responseMessage.id
							// This ensures the ID matches what the client received in the start chunk
							await persistAssistantMessage(
								threadId,
								assistantMessageId,
								parts,
								message.id,
								logger,
							);
							await updateTitleIfNeeded(threadId, thread?.title, message, logger);
						},
						onError: (error) => {
							logger.error({ err: extractErrorMessage(error) }, "Streaming error");
							return error instanceof Error ? error.message : "An error occurred";
						},
					}),
				);

				await result.consumeStream();
			} catch (err) {
				logger.error({ err: extractErrorMessage(err) }, "Stream execution failed");
			}
		},
		onError: (e) => {
			const msg = e instanceof Error ? e.message : "An error occurred";
			return IS_PRODUCTION ? "An error occurred" : msg;
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
