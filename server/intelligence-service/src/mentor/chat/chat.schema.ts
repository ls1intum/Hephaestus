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
	message: messageSchema.optional(),
	previousMessageId: z.string().optional(),
	greeting: z.boolean().optional().describe("If true, generate a greeting without user message"),
});

export type ChatRequestBody = z.infer<typeof chatRequestBodySchema>;

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
	UpdateDocumentInput,
	UpdateDocumentOutput,
} from "./chat.shared";
