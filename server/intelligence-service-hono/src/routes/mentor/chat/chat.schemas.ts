import type { InferUITool, UIMessage } from "ai";
import { z } from "zod";
import type { getWeather } from "@/lib/ai/tools/get-weather";
import type { AppUsage } from "@/lib/ai/usage";

// Types and schemas for mentor chat

export type DataPart = { type: "append-message"; message: string };

export const messageMetadataSchema = z.object({
	createdAt: z.string(),
});

export type MessageMetadata = z.infer<typeof messageMetadataSchema>;

type weatherTool = InferUITool<typeof getWeather>;

export type ChatTools = {
	getWeather: weatherTool;
};

export type CustomUIDataTypes = {
	textDelta: string;
	appendMessage: string;
	id: string;
	title: string;
	kind: "text";
	clear: null;
	finish: null;
	usage: AppUsage;
};

export type ChatMessage = UIMessage<
	MessageMetadata,
	CustomUIDataTypes,
	ChatTools
>;

export type Attachment = {
	name: string;
	url: string;
	contentType: string;
};

// Stream part schema for server-sent events

export const streamTextStartPartSchema = z.object({
	type: z.literal("text-start"),
	id: z.string(),
	providerMetadata: z.unknown().optional(),
});

export const streamTextDeltaPartSchema = z.object({
	type: z.literal("text-delta"),
	id: z.string(),
	delta: z.string(),
	providerMetadata: z.unknown().optional(),
});

export const streamTextEndPartSchema = z.object({
	type: z.literal("text-end"),
	id: z.string(),
	providerMetadata: z.unknown().optional(),
});

export const streamErrorPartSchema = z.object({
	type: z.literal("error"),
	errorText: z.string(),
});

export const streamToolInputStartPartSchema = z.object({
	type: z.literal("tool-input-start"),
	toolCallId: z.string(),
	toolName: z.string(),
	providerExecuted: z.boolean().optional(),
	dynamic: z.boolean().optional(),
});

export const streamToolInputDeltaPartSchema = z.object({
	type: z.literal("tool-input-delta"),
	toolCallId: z.string(),
	inputTextDelta: z.string(),
});

export const streamToolInputAvailablePartSchema = z.object({
	type: z.literal("tool-input-available"),
	toolCallId: z.string(),
	toolName: z.string(),
	input: z.unknown(),
	providerExecuted: z.boolean().optional(),
	providerMetadata: z.unknown().optional(),
	dynamic: z.boolean().optional(),
});

export const streamToolInputErrorPartSchema = z.object({
	type: z.literal("tool-input-error"),
	toolCallId: z.string(),
	toolName: z.string(),
	input: z.unknown(),
	errorText: z.string(),
	providerExecuted: z.boolean().optional(),
	providerMetadata: z.unknown().optional(),
	dynamic: z.boolean().optional(),
});

export const streamToolOutputAvailablePartSchema = z.object({
	type: z.literal("tool-output-available"),
	toolCallId: z.string(),
	output: z.unknown(),
	providerExecuted: z.boolean().optional(),
	dynamic: z.boolean().optional(),
});

export const streamToolOutputErrorPartSchema = z.object({
	type: z.literal("tool-output-error"),
	toolCallId: z.string(),
	errorText: z.string(),
	providerExecuted: z.boolean().optional(),
	dynamic: z.boolean().optional(),
});

export const streamReasoningStartPartSchema = z.object({
	type: z.literal("reasoning-start"),
	id: z.string(),
	providerMetadata: z.unknown().optional(),
});

export const streamReasoningDeltaPartSchema = z.object({
	type: z.literal("reasoning-delta"),
	id: z.string(),
	delta: z.string(),
	providerMetadata: z.unknown().optional(),
});

export const streamReasoningEndPartSchema = z.object({
	type: z.literal("reasoning-end"),
	id: z.string(),
	providerMetadata: z.unknown().optional(),
});

export const streamSourceUrlPartSchema = z.object({
	type: z.literal("source-url"),
	sourceId: z.string(),
	url: z.string(),
	title: z.string().optional(),
	providerMetadata: z.unknown().optional(),
});

export const streamSourceDocumentPartSchema = z.object({
	type: z.literal("source-document"),
	sourceId: z.string(),
	mediaType: z.string(),
	title: z.string(),
	filename: z.string().optional(),
	providerMetadata: z.unknown().optional(),
});

export const streamFilePartSchema = z.object({
	type: z.literal("file"),
	url: z.string(),
	mediaType: z.string(),
	providerMetadata: z.unknown().optional(),
});

export const streamDataPartSchema = z.object({
	type: z.string().regex(/^data-.+/),
	id: z.string().optional(),
	data: z.unknown(),
	transient: z.boolean().optional(),
});

export const streamStepStartPartSchema = z.object({
	type: z.literal("start-step"),
});

export const streamStepFinishPartSchema = z.object({
	type: z.literal("finish-step"),
});

export const streamStartPartSchema = z.object({
	type: z.literal("start"),
	messageId: z.string().optional(),
	messageMetadata: z.unknown().optional(),
});

export const streamFinishPartSchema = z.object({
	type: z.literal("finish"),
	messageMetadata: z.unknown().optional(),
});

export const streamMessageMetadataPartSchema = z.object({
	type: z.literal("message-metadata"),
	messageMetadata: z.unknown(),
});

export const streamAbortPartSchema = z.object({
	type: z.literal("abort"),
});

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

export const chatRequestBodySchema = z.object({
	id: z.string().uuid(),
	message: z.object({
		id: z.string().uuid(),
		role: z.enum(["user"]),
		parts: z.array(partSchema),
	}),
	previousMessageId: z.string().uuid().optional(),
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
const uiFilePartSchema = z.object({
	type: z.literal("file"),
	url: z.string().url(),
	mediaType: z.enum(["image/jpeg", "image/png"]),
	name: z.string().optional(),
});

export const threadMessageSchema = z.object({
	id: z.string().uuid(),
	role: z.enum(["system", "user", "assistant"]),
	parts: z.array(z.union([uiTextPartSchema, uiFilePartSchema])),
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
