import type { InferUITool, UIMessage } from "ai";
import { tool } from "ai";
import { z } from "zod";
import type { getWeather } from "../../../lib/ai/tools/get-weather";
import type { AppUsage } from "../../../lib/ai/usage";

const createDocumentInputSchema = z.object({
	title: z.string().min(1).max(255),
	kind: z.literal("text"),
});

const createDocumentTypeHelper = tool({
	description: "type helper",
	inputSchema: createDocumentInputSchema,
	execute: async () => ({
		id: "",
		title: "",
		kind: "text" as const,
		content: "",
	}),
});

const updateDocumentInputSchema = z.object({
	id: z.string().uuid(),
	description: z.string(),
});

type UpdateInput = z.infer<typeof updateDocumentInputSchema>;

const updateDocumentTypeHelper = tool({
	description: "type helper",
	inputSchema: updateDocumentInputSchema,
	execute: async ({ id }: UpdateInput) => ({
		id,
		title: "",
		kind: "text" as const,
		content: "",
		description: "",
	}),
});

export type GetWeatherTool = InferUITool<typeof getWeather>;
export type GetWeatherInput = GetWeatherTool["input"];
export type GetWeatherOutput = GetWeatherTool["output"];

export type CreateDocumentTool = InferUITool<typeof createDocumentTypeHelper>;
export type CreateDocumentInput = z.infer<typeof createDocumentInputSchema>;
export type CreateDocumentOutput = CreateDocumentTool["output"];

export type UpdateDocumentTool = InferUITool<typeof updateDocumentTypeHelper>;
export type UpdateDocumentInput = z.infer<typeof updateDocumentInputSchema>;
export type UpdateDocumentOutput = UpdateDocumentTool["output"];

export type ChatTools = {
	getWeather: GetWeatherTool;
	createDocument: CreateDocumentTool;
	updateDocument: UpdateDocumentTool;
};

export type DocumentKind = CreateDocumentInput["kind"];

export interface DocumentCreateData {
	id: string;
	title: string;
	kind: DocumentKind;
}

export interface DocumentUpdateData {
	id: string;
	kind: DocumentKind;
}

export interface DocumentDeltaData {
	id: string;
	kind: DocumentKind;
	delta: string;
}

export interface DocumentFinishData {
	id: string;
	kind: DocumentKind;
}

/**
 * Custom UI data types for streaming document artifacts.
 * Keys become `data-{key}` in the stream (e.g., "document-create" → "data-document-create").
 */
export type CustomUIDataTypes = {
	"document-create": DocumentCreateData;
	"document-update": DocumentUpdateData;
	"document-delta": DocumentDeltaData;
	"document-finish": DocumentFinishData;
	usage: AppUsage;
};

/**
 * Document-specific data types (excludes usage).
 * Use this for handlers that only care about document streaming events.
 */
export type DocumentDataTypes = Omit<CustomUIDataTypes, "usage">;

export interface MessageMetadata {
	createdAt: string;
}

export type ChatMessage = UIMessage<
	MessageMetadata,
	CustomUIDataTypes,
	ChatTools
>;
