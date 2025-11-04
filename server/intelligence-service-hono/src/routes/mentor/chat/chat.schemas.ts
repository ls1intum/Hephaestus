import { z } from "@hono/zod-openapi";

// Types and schemas for mentor chat

export type DataPart = { type: "append-message"; message: string };

export const messageMetadataSchema = z.object({
	createdAt: z.string(),
});

export type MessageMetadata = z.infer<typeof messageMetadataSchema>;

export type Attachment = {
	name: string;
	url: string;
	contentType: string;
};

// Stream part schema for server-sent events

export const streamTextStartPartSchema = z
	.object({
		type: z.literal("text-start"),
		id: z.string(),
		providerMetadata: z.unknown().optional(),
	})
	.openapi("StreamTextStartPart");

export const streamTextDeltaPartSchema = z
	.object({
		type: z.literal("text-delta"),
		id: z.string(),
		delta: z.string(),
		providerMetadata: z.unknown().optional(),
	})
	.openapi("StreamTextDeltaPart");

export const streamTextEndPartSchema = z
	.object({
		type: z.literal("text-end"),
		id: z.string(),
		providerMetadata: z.unknown().optional(),
	})
	.openapi("StreamTextEndPart");

export const streamErrorPartSchema = z
	.object({
		type: z.literal("error"),
		errorText: z.string(),
	})
	.openapi("StreamErrorPart");

export const streamToolInputStartPartSchema = z
	.object({
		type: z.literal("tool-input-start"),
		toolCallId: z.string(),
		toolName: z.string(),
		providerExecuted: z.boolean().optional(),
		dynamic: z.boolean().optional(),
	})
	.openapi("StreamToolInputStartPart");

export const streamToolInputDeltaPartSchema = z
	.object({
		type: z.literal("tool-input-delta"),
		toolCallId: z.string(),
		inputTextDelta: z.string(),
	})
	.openapi("StreamToolInputDeltaPart");

export const streamToolInputAvailablePartSchema = z
	.object({
		type: z.literal("tool-input-available"),
		toolCallId: z.string(),
		toolName: z.string(),
		input: z.unknown(),
		providerExecuted: z.boolean().optional(),
		providerMetadata: z.unknown().optional(),
		dynamic: z.boolean().optional(),
	})
	.openapi("StreamToolInputAvailablePart");

export const streamToolInputErrorPartSchema = z
	.object({
		type: z.literal("tool-input-error"),
		toolCallId: z.string(),
		toolName: z.string(),
		input: z.unknown(),
		errorText: z.string(),
		providerExecuted: z.boolean().optional(),
		providerMetadata: z.unknown().optional(),
		dynamic: z.boolean().optional(),
	})
	.openapi("StreamToolInputErrorPart");

export const streamToolOutputAvailablePartSchema = z
	.object({
		type: z.literal("tool-output-available"),
		toolCallId: z.string(),
		output: z.unknown(),
		providerExecuted: z.boolean().optional(),
		dynamic: z.boolean().optional(),
	})
	.openapi("StreamToolOutputAvailablePart");

export const streamToolOutputErrorPartSchema = z
	.object({
		type: z.literal("tool-output-error"),
		toolCallId: z.string(),
		errorText: z.string(),
		providerExecuted: z.boolean().optional(),
		dynamic: z.boolean().optional(),
	})
	.openapi("StreamToolOutputErrorPart");

export const streamReasoningStartPartSchema = z
	.object({
		type: z.literal("reasoning-start"),
		id: z.string(),
		providerMetadata: z.unknown().optional(),
	})
	.openapi("StreamReasoningStartPart");

export const streamReasoningDeltaPartSchema = z
	.object({
		type: z.literal("reasoning-delta"),
		id: z.string(),
		delta: z.string(),
		providerMetadata: z.unknown().optional(),
	})
	.openapi("StreamReasoningDeltaPart");

export const streamReasoningEndPartSchema = z
	.object({
		type: z.literal("reasoning-end"),
		id: z.string(),
		providerMetadata: z.unknown().optional(),
	})
	.openapi("StreamReasoningEndPart");

export const streamSourceUrlPartSchema = z
	.object({
		type: z.literal("source-url"),
		sourceId: z.string(),
		url: z.string(),
		title: z.string().optional(),
		providerMetadata: z.unknown().optional(),
	})
	.openapi("StreamSourceUrlPart");

export const streamSourceDocumentPartSchema = z
	.object({
		type: z.literal("source-document"),
		sourceId: z.string(),
		mediaType: z.string(),
		title: z.string(),
		filename: z.string().optional(),
		providerMetadata: z.unknown().optional(),
	})
	.openapi("StreamSourceDocumentPart");

export const streamFilePartSchema = z
	.object({
		type: z.literal("file"),
		url: z.string(),
		mediaType: z.string(),
		providerMetadata: z.unknown().optional(),
	})
	.openapi("StreamFilePart");

export const streamDataPartSchema = z
	.object({
		type: z.string().regex(/^data-.+/),
		id: z.string().optional(),
		data: z.unknown(),
		transient: z.boolean().optional(),
	})
	.openapi("StreamDataPart");

export const streamStepStartPartSchema = z
	.object({
		type: z.literal("start-step"),
	})
	.openapi("StreamStepStartPart");

export const streamStepFinishPartSchema = z
	.object({
		type: z.literal("finish-step"),
	})
	.openapi("StreamStepFinishPart");

export const streamStartPartSchema = z
	.object({
		type: z.literal("start"),
		messageId: z.string().optional(),
		messageMetadata: z.unknown().optional(),
	})
	.openapi("StreamStartPart");

export const streamFinishPartSchema = z
	.object({
		type: z.literal("finish"),
		messageMetadata: z.unknown().optional(),
	})
	.openapi("StreamFinishPart");

export const streamMessageMetadataPartSchema = z
	.object({
		type: z.literal("message-metadata"),
		messageMetadata: z.unknown(),
	})
	.openapi("StreamMessageMetadataPart");

export const streamAbortPartSchema = z
	.object({
		type: z.literal("abort"),
	})
	.openapi("StreamAbortPart");

export const streamPartSchema = z.union([
	streamTextStartPartSchema,
	streamTextDeltaPartSchema,
	streamTextEndPartSchema,
	streamErrorPartSchema,
	streamToolInputStartPartSchema,
	streamToolInputDeltaPartSchema,
	streamToolInputAvailablePartSchema,
	streamToolInputErrorPartSchema,
	streamToolOutputAvailablePartSchema,
	streamToolOutputErrorPartSchema,
	streamReasoningStartPartSchema,
	streamReasoningDeltaPartSchema,
	streamReasoningEndPartSchema,
	streamSourceUrlPartSchema,
	streamSourceDocumentPartSchema,
	streamFilePartSchema,
	streamDataPartSchema,
	streamStepStartPartSchema,
	streamStepFinishPartSchema,
	streamStartPartSchema,
	streamFinishPartSchema,
	streamMessageMetadataPartSchema,
	streamAbortPartSchema,
]);

export type StreamPart = z.infer<typeof streamPartSchema>;

// Request body schema for /mentor/chat

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

// Thread detail response schema for GET /mentor/threads/:threadId
export const ThreadIdParamsSchema = z
	.object({ threadId: z.string().uuid() })
	.openapi("ThreadIdParams");
export type ThreadIdParams = z.infer<typeof ThreadIdParamsSchema>;

const uiTextPartSchema = z.object({
	type: z.literal("text"),
	text: z.string(),
});
const uiReasoningPartSchema = z.object({
	type: z.literal("reasoning"),
	text: z.string(),
	providerMetadata: z.unknown().optional(),
});
const uiFilePartSchema = z.object({
	type: z.literal("file"),
	url: z.string().url(),
	mediaType: z.enum(["image/jpeg", "image/png"]),
	name: z.string().optional(),
});
const uiToolPartSchema = z
	.object({
		type: z.string().regex(/^tool-/),
	})
	.passthrough();
const uiSourceUrlPartSchema = z.object({
	type: z.literal("source-url"),
	url: z.string(),
	title: z.string().optional(),
	sourceId: z.string().optional(),
	providerMetadata: z.unknown().optional(),
});
const uiSourceDocumentPartSchema = z.object({
	type: z.literal("source-document"),
	mediaType: z.string(),
	title: z.string(),
	filename: z.string().optional(),
	providerMetadata: z.unknown().optional(),
});
// Fallback for forward-compatibility with new part types
const uiUnknownPartSchema = z.object({ type: z.string() }).passthrough();

export const threadMessageSchema = z.object({
	id: z.string().uuid(),
	role: z.enum(["system", "user", "assistant"]),
	parts: z.array(
		z.union([
			uiTextPartSchema,
			uiReasoningPartSchema,
			uiFilePartSchema,
			uiToolPartSchema,
			uiSourceUrlPartSchema,
			uiSourceDocumentPartSchema,
			uiUnknownPartSchema,
		]),
	),
	createdAt: z.string().datetime().optional(),
	parentMessageId: z.string().uuid().nullable().optional(),
});

export const threadDetailSchema = z.object({
	id: z.string().uuid(),
	title: z.string().nullable().optional(),
	selectedLeafMessageId: z.string().uuid().nullable().optional(),
	messages: z.array(threadMessageSchema),
});

export type ThreadDetail = z.infer<typeof threadDetailSchema>;

export type {
	ChatMessage,
	ChatTools,
	CustomUIDataTypes,
	CreateDocumentInput,
	CreateDocumentOutput,
	UpdateDocumentInput,
	UpdateDocumentOutput,
	GetWeatherInput,
	GetWeatherOutput,
	DocumentCreateData,
	DocumentUpdateData,
	DocumentDeltaData,
	DocumentFinishData,
} from "./chat.shared";
