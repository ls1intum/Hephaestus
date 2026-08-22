/**
 * Chat message validation utilities.
 *
 * Provides runtime validation for chat messages received from the server.
 * Uses Zod schemas to safely parse and validate data before it reaches the chat UI.
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
 * The part union stays open — the mentor streams part kinds the client ignores — so the schema
 * checks the envelope every consumer relies on (id, role, and a `type` on each part) and the
 * renderers narrow each part before reading its fields.
 */
function isChatMessageArray(value: unknown): value is ChatMessage[] {
	const result = chatMessagesArraySchema.safeParse(value);
	if (!result.success) {
		console.warn("[parseThreadMessages] Validation failed:", result.error);
		return false;
	}
	return true;
}

/**
 * Safely parse and validate an array of chat messages.
 * Returns validated messages or undefined if validation fails.
 *
 * @param messages - Unknown messages array from server response
 * @returns Validated ChatMessage[] or undefined
 */
export function parseThreadMessages(messages: unknown): ChatMessage[] | undefined {
	return isChatMessageArray(messages) ? messages : undefined;
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

	if (!("votes" in threadDetail) || !Array.isArray(threadDetail.votes)) {
		return [];
	}

	const result = votesArraySchema.safeParse(threadDetail.votes);
	if (!result.success) {
		console.warn("[extractVotesFromThreadDetail] Validation failed:", result.error);
		return [];
	}

	return result.data;
}
