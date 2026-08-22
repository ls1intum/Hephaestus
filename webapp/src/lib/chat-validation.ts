/**
 * Chat message validation utilities.
 *
 * Provides runtime validation for chat messages received from the server.
 */

import { z } from "zod";
import type { ChatMessage } from "@/lib/types";

// ─────────────────────────────────────────────────────────────────────────────
// Message Validation Schema
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A part is anything carrying a `type`. Unknown keys survive validation: the mentor streams part
 * kinds this client does not model, and stripping their payload here would leave the renderer
 * nothing to narrow.
 *
 * A text part is the one shape whose payload is checked, because the renderer reads `text`
 * unguarded — the AI SDK types it as always present, so a part that omits it would reach
 * `String.prototype.replaceAll` and take the whole conversation down.
 */
const messagePartSchema = z
	.looseObject({ type: z.string() })
	.refine((part) => part.type !== "text" || typeof part.text === "string", {
		message: "A text part must carry its text",
	});

/**
 * Mirrors the `ThreadDetail.messages` structure. Unknown keys survive here too, because the AI SDK
 * hangs its own bookkeeping off a message and the chat UI passes those fields straight back.
 */
const chatMessageSchema = z.looseObject({
	id: z.uuid(),
	role: z.enum(["system", "user", "assistant"]),
	parts: z.array(messagePartSchema),
	createdAt: z.iso.datetime().optional(),
});

const chatMessagesArraySchema = z.array(chatMessageSchema);

// ─────────────────────────────────────────────────────────────────────────────
// Validation Functions
// ─────────────────────────────────────────────────────────────────────────────

/** False for anything the schema rejects, logging the reason it was rejected. */
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
	messageId: z.uuid().optional(),
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
