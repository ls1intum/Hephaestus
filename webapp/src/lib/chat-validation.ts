import type { ChatStatus } from "ai";
import { z } from "zod";

import type { ChatMessage as ThreadMessage } from "@/api/types.gen";
import { hasText } from "@/lib/text";
import type { ChatMessage, ChatTurn } from "@/lib/types";

/**
 * A `data-result` part's data: Heph linked feedback to one practice result. A link stored without
 * `text` showed nothing and proves nothing.
 */
export const resultLinkDataSchema = z.object({
	resultId: z.uuid(),
	text: z.string().optional(),
});

/** A `data-problem` part's data: the same link, narrowed to one problem of the result. */
export const problemLinkDataSchema = resultLinkDataSchema.extend({
	problemId: z.uuid(),
});

/** Every part kind that links feedback, with the check its payload must pass. */
const FEEDBACK_LINK_SCHEMAS: Record<string, z.ZodType<{ text?: string }> | undefined> = {
	"data-result": resultLinkDataSchema,
	"data-problem": problemLinkDataSchema,
};

/** A `data-mentor-status` part's data. */
export const mentorStatusDataSchema = z.object({
	state: z.enum(["warming-up", "busy", "conflict"]),
	reason: z.string().nullish(),
});

export type MentorTurnState = z.infer<typeof mentorStatusDataSchema>["state"];

export function mentorStatus(part: { type: string; data: unknown }): MentorTurnState | undefined {
	if (part.type !== "data-mentor-status") {
		return undefined;
	}
	const parsed = mentorStatusDataSchema.safeParse(part.data);
	return parsed.success ? parsed.data.state : undefined;
}

/** The turn `useChat` reports, read with the latest state the server streamed for it. */
export function mentorTurn(status: ChatStatus, state: MentorTurnState | undefined): ChatTurn {
	switch (status) {
		case "ready": {
			return { kind: "ready" };
		}
		case "error": {
			return { kind: "error", failure: state === "busy" ? "busy" : "failed" };
		}
		case "submitted":
		case "streaming": {
			return { kind: status, warmingUp: state === "warming-up" };
		}
	}
}

/** Parsed where it is read, so a streamed part and a stored one pass the same check. */
export function shownFeedbackText(part: ChatMessage["parts"][number]): string | undefined {
	const schema = FEEDBACK_LINK_SCHEMAS[part.type];
	if (schema === undefined || !("data" in part)) {
		return undefined;
	}
	const parsed = schema.safeParse(part.data);
	return parsed.success && hasText(parsed.data.text?.trim()) ? parsed.data.text : undefined;
}

/**
 * Unknown keys survive: the mentor streams part kinds this client does not model, and stripping
 * their payload would leave the renderer nothing to narrow on. `text` is the one payload checked,
 * because the AI SDK types it as always present and the renderer reads it unguarded — a text part
 * that omitted it would throw mid-transcript rather than render short.
 */
const messagePartSchema = z
	.looseObject({ type: z.string() })
	.refine((part) => part.type !== "text" || typeof part.text === "string", {
		message: "A text part must carry its text",
	});

/** Loose again: the AI SDK hangs its own bookkeeping off a message and the chat UI passes it back. */
const chatMessageSchema = z.looseObject({
	id: z.uuid(),
	role: z.enum(["system", "user", "assistant"]),
	parts: z.array(messagePartSchema),
	createdAt: z.date().optional() satisfies z.ZodType<ThreadMessage["createdAt"]>,
});

const chatMessagesArraySchema = z.array(chatMessageSchema);

function isChatMessageArray(value: unknown): value is ChatMessage[] {
	return chatMessagesArraySchema.safeParse(value).success;
}

export function parseThreadMessages(messages: unknown): ChatMessage[] | undefined {
	return isChatMessageArray(messages) ? messages : undefined;
}
