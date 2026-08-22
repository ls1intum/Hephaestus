import { z } from "zod";
import type { ChatMessage } from "@/lib/types";

/**
 * Unknown keys survive: the mentor streams part kinds this client does not model, and stripping
 * their payload would leave the renderer nothing to narrow. `text` is the one payload checked,
 * because the renderer reads it unguarded — the AI SDK types it as always present, so a text part
 * that omits it would reach `String.prototype.replaceAll` and take the conversation down.
 */
const messagePartSchema = z
	.looseObject({ type: z.string() })
	.refine((part) => part.type !== "text" || typeof part.text === "string", {
		message: "A text part must carry its text",
	});

/**
 * Unknown keys survive here too: the AI SDK hangs its own bookkeeping off a message and the chat UI
 * passes those fields straight back.
 */
const chatMessageSchema = z.looseObject({
	id: z.uuid(),
	role: z.enum(["system", "user", "assistant"]),
	parts: z.array(messagePartSchema),
	createdAt: z.iso.datetime().optional(),
});

const chatMessagesArraySchema = z.array(chatMessageSchema);

function isChatMessageArray(value: unknown): value is ChatMessage[] {
	const result = chatMessagesArraySchema.safeParse(value);
	if (!result.success) {
		console.warn("[parseThreadMessages] Validation failed:", result.error);
		return false;
	}
	return true;
}

export function parseThreadMessages(messages: unknown): ChatMessage[] | undefined {
	return isChatMessageArray(messages) ? messages : undefined;
}

const voteSchema = z.object({
	messageId: z.uuid().optional(),
	isUpvoted: z.boolean().optional(),
});

const votesArraySchema = z.array(voteSchema);

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
