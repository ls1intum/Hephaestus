import { z } from "@hono/zod-openapi";

// ─────────────────────────────────────────────────────────────────────────────
// Thread List Schemas (GET /mentor/threads/grouped)
// ─────────────────────────────────────────────────────────────────────────────

export const ChatThreadSummarySchema = z
	.object({
		id: z.string().uuid(),
		title: z.string(),
		createdAt: z.string().datetime().optional(),
	})
	.openapi("ChatThreadSummary");
export type ChatThreadSummary = z.infer<typeof ChatThreadSummarySchema>;

export const ChatThreadGroupSchema = z
	.object({
		groupName: z.string(),
		threads: z.array(ChatThreadSummarySchema),
	})
	.openapi("ChatThreadGroup");
export type ChatThreadGroup = z.infer<typeof ChatThreadGroupSchema>;

// ─────────────────────────────────────────────────────────────────────────────
// Thread Detail Schemas (GET /mentor/threads/{threadId})
// ─────────────────────────────────────────────────────────────────────────────

export const ThreadIdParamsSchema = z
	.object({ threadId: z.string().uuid() })
	.openapi("ThreadIdParams");
export type ThreadIdParams = z.infer<typeof ThreadIdParamsSchema>;

/**
 * Message part schema - uses passthrough for forward compatibility.
 * The transformer validates specific part types; this schema allows any part
 * with a `type` field through for OpenAPI documentation purposes.
 */
const messagePartSchema = z.object({ type: z.string() }).passthrough();

export const threadMessageSchema = z.object({
	id: z.string().uuid(),
	role: z.enum(["system", "user", "assistant"]),
	parts: z.array(messagePartSchema),
	createdAt: z.string().datetime().optional(),
	parentMessageId: z.string().uuid().nullable().optional(),
});

export const threadDetailSchema = z
	.object({
		id: z.string().uuid(),
		title: z.string().nullable().optional(),
		selectedLeafMessageId: z.string().uuid().nullable().optional(),
		messages: z.array(threadMessageSchema),
	})
	.openapi("ThreadDetail");

export type ThreadDetail = z.infer<typeof threadDetailSchema>;
