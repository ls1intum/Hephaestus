/**
 * Chat message validation utilities.
 *
 * Provides runtime validation for chat messages received from the server.
 * Uses Zod schemas to safely parse and validate data before type assertion.
 */

import { z } from "zod";
import type { ChatMessage } from "@/lib/types";

// ─────────────────────────────────────────────────────────────────────────────
// Message Validation Schema
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Message part schema with passthrough for forward compatibility.
 * Allows any part with a `type` field through.
 */
const messagePartSchema = z.object({ type: z.string() }).passthrough();

/**
 * Chat message schema matching the ThreadDetail.messages structure.
 * Uses passthrough at the message level for additional AI SDK fields.
 */
const chatMessageSchema = z
	.object({
		id: z.string().uuid(),
		role: z.enum(["system", "user", "assistant"]),
		parts: z.array(messagePartSchema),
		createdAt: z.string().datetime().optional(),
	})
	.passthrough();

const chatMessagesArraySchema = z.array(chatMessageSchema);

// ─────────────────────────────────────────────────────────────────────────────
// Validation Functions
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Safely parse and validate an array of chat messages.
 * Returns validated messages or undefined if validation fails.
 *
 * @param messages - Unknown messages array from server response
 * @returns Validated ChatMessage[] or undefined
 */
export function parseThreadMessages(
	messages: unknown,
): ChatMessage[] | undefined {
	const result = chatMessagesArraySchema.safeParse(messages);
	if (!result.success) {
		console.warn("[parseThreadMessages] Validation failed:", result.error);
		return undefined;
	}
	// Schema validated the structure, safe to cast to ChatMessage[]
	return result.data as unknown as ChatMessage[];
}

/**
 * Parse a single chat message for validation.
 *
 * @param message - Unknown message from stream
 * @returns Validated ChatMessage or undefined
 */
export function parseSingleMessage(message: unknown): ChatMessage | undefined {
	const result = chatMessageSchema.safeParse(message);
	if (!result.success) {
		console.warn("[parseSingleMessage] Validation failed:", result.error);
		return undefined;
	}
	return result.data as unknown as ChatMessage;
}

// ─────────────────────────────────────────────────────────────────────────────
// Vote Validation
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Vote schema for validating votes from thread detail.
 */
const voteSchema = z.object({
	messageId: z.string().uuid().optional(),
	isUpvoted: z.boolean().optional(),
});

const votesArraySchema = z.array(voteSchema);

/**
 * Safely extract votes from thread detail.
 *
 * @param threadDetail - Thread detail object that may contain votes
 * @returns Array of vote objects with messageId and isUpvoted
 */
export function extractVotesFromThreadDetail(
	threadDetail: unknown,
): Array<{ messageId?: string; isUpvoted?: boolean }> {
	if (!threadDetail || typeof threadDetail !== "object") {
		return [];
	}

	const detail = threadDetail as Record<string, unknown>;
	if (!("votes" in detail) || !Array.isArray(detail.votes)) {
		return [];
	}

	const result = votesArraySchema.safeParse(detail.votes);
	if (!result.success) {
		console.warn(
			"[extractVotesFromThreadDetail] Validation failed:",
			result.error,
		);
		return [];
	}

	return result.data;
}
