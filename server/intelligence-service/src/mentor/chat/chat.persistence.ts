/**
 * Chat persistence operations.
 *
 * This module handles all database operations for chat,
 * with proper error handling and reporting.
 */

import type { HandlerLogger } from "@/shared/utils";
import { extractErrorMessage } from "@/shared/utils";
import type { ChatRequestBody } from "./chat.schema";
import { inferTitleFromMessage, partsToPersist } from "./chat.transformer";
import type { PersistedPart } from "./data";
import {
	createThread,
	getThreadById,
	saveMessage,
	updateSelectedLeafMessageId,
	updateThreadTitle,
} from "./data";

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

export type PersistenceResult<T> = { success: true; data: T } | { success: false; error: string };

type Thread = Awaited<ReturnType<typeof getThreadById>>;

// ─────────────────────────────────────────────────────────────────────────────
// Thread Operations
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Load or create a thread for a chat session.
 * Returns null if persistence fails (allows graceful degradation).
 * Pass null for message when creating a greeting-only thread.
 */
export async function loadOrCreateThread(
	threadId: string,
	workspaceId: number | null,
	message: ChatRequestBody["message"] | null,
	logger: HandlerLogger,
): Promise<PersistenceResult<Thread>> {
	if (!workspaceId) {
		logger.warn({ threadId }, "Thread persistence skipped - missing workspace ID");
		return { success: false, error: "Missing workspace ID" };
	}

	try {
		let thread = await getThreadById(threadId);

		if (!thread) {
			logger.debug({ threadId, workspaceId }, "Creating new thread");
			thread = await createThread({
				id: threadId,
				title: message ? inferTitleFromMessage(message) : "New chat",
				workspaceId,
			});
			logger.debug({ threadId, created: !!thread }, "Thread creation result");
		}

		return { success: true, data: thread ?? null };
	} catch (err) {
		const error = extractErrorMessage(err);
		const isFKViolation = error.includes("violates foreign key constraint");
		logger.warn(
			{ err: error, threadId, workspaceId, isFKViolation },
			isFKViolation
				? "Thread persistence failed - foreign key constraint violated (workspace may not exist)"
				: "Thread persistence failed",
		);
		return { success: false, error };
	}
}

// ─────────────────────────────────────────────────────────────────────────────
// Message Operations
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Persist a user message to the database.
 */
export async function persistUserMessage(
	threadId: string,
	message: NonNullable<ChatRequestBody["message"]>,
	previousMessageId: string | undefined,
	logger: HandlerLogger,
): Promise<PersistenceResult<string>> {
	try {
		await saveMessage({
			id: message.id,
			role: "user",
			threadId,
			parts: partsToPersist(message.parts) as PersistedPart[],
			parentMessageId: previousMessageId || null,
			createdAt: new Date(),
		});

		return { success: true, data: message.id };
	} catch (err) {
		const error = extractErrorMessage(err);
		const isFKViolation = error.includes("violates foreign key constraint");
		logger.warn(
			{ err: error, threadId, messageId: message.id, isFKViolation },
			isFKViolation
				? "User message persistence failed - thread may not exist (FK violation)"
				: "User message persistence failed",
		);
		return { success: false, error };
	}
}

/**
 * Persist an assistant message after streaming completes.
 */
export async function persistAssistantMessage(
	threadId: string,
	assistantId: string,
	parts: Array<{ type: string; [k: string]: unknown }>,
	parentMessageId: string,
	logger: HandlerLogger,
): Promise<PersistenceResult<string>> {
	try {
		await saveMessage({
			id: assistantId,
			role: "assistant",
			threadId,
			parts: partsToPersist(parts) as PersistedPart[],
			parentMessageId,
			createdAt: new Date(),
		});

		await updateSelectedLeafMessageId(threadId, assistantId);

		return { success: true, data: assistantId };
	} catch (err) {
		const error = extractErrorMessage(err);
		const isFKViolation = error.includes("violates foreign key constraint");
		logger.warn(
			{ err: error, threadId, assistantId, isFKViolation },
			isFKViolation
				? "Assistant message persistence failed - thread may not exist (FK violation)"
				: "Assistant message persistence failed",
		);
		return { success: false, error };
	}
}

/**
 * Update thread title if not already set.
 */
export async function updateTitleIfNeeded(
	threadId: string,
	currentTitle: string | null | undefined,
	message: NonNullable<ChatRequestBody["message"]>,
	logger: HandlerLogger,
): Promise<void> {
	if (currentTitle) {
		return;
	}

	try {
		const title = inferTitleFromMessage(message);
		await updateThreadTitle(threadId, title);
	} catch (err) {
		logger.warn({ err: extractErrorMessage(err), threadId }, "Title update failed");
	}
}
