import { z } from "@hono/zod-openapi";

/**
 * Chat schemas for API request/response validation.
 *
 * Note: Stream parts are NOT defined here. The AI SDK handles streaming
 * internally via createUIMessageStreamResponse(). OpenAPI docs use a
 * passthrough schema since the stream format is defined by the AI SDK.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Stream Response Schema (for OpenAPI docs only)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Passthrough schema for SSE stream responses.
 * The actual stream format is defined by AI SDK's UIMessageStream.
 * We don't validate individual stream parts server-side.
 */
export const streamPartSchema = z
	.object({
		type: z.string(),
	})
	.passthrough()
	.openapi("StreamPart");

// ─────────────────────────────────────────────────────────────────────────────
// Request Body Schema for POST /mentor/chat
// ─────────────────────────────────────────────────────────────────────────────

const textPartSchema = z.object({
	type: z.enum(["text"]),
	text: z.string().min(1).max(2000),
});

const filePartSchema = z.object({
	type: z.enum(["file"]),
	mediaType: z.enum(["image/jpeg", "image/png"]),
	name: z.string().min(1).max(100),
	url: z.string().url(),
});

const partSchema = z.union([textPartSchema, filePartSchema]);

export const messageSchema = z.object({
	id: z.string().uuid(),
	role: z.enum(["user"]),
	parts: z.array(partSchema),
});

export const chatRequestBodySchema = z.object({
	id: z.string().uuid(),
	message: messageSchema,
	previousMessageId: z.string().optional(),
});

export type ChatRequestBody = z.infer<typeof chatRequestBodySchema>;

// ─────────────────────────────────────────────────────────────────────────────
// Thread Detail Response Schema for GET /mentor/threads/:threadId
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

// Re-export types from chat.shared for webapp compatibility
export type {
	ChatMessage,
	ChatTools,
	CreateDocumentInput,
	CreateDocumentOutput,
	CustomUIDataTypes,
	DocumentCreateData,
	DocumentDeltaData,
	DocumentFinishData,
	DocumentUpdateData,
	GetWeatherInput,
	GetWeatherOutput,
	UpdateDocumentInput,
	UpdateDocumentOutput,
} from "./chat.shared";
