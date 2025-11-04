import { tool } from "ai";
import type { InferUITool, UIMessage } from "ai";
import { z } from "zod";
import type { AppUsage } from "../../../lib/ai/usage";
import type { getWeather } from "../../../lib/ai/tools/get-weather";

const createDocumentTypeHelper = tool({
	description: "type helper",
	inputSchema: z.object({
	title: z.string().min(1).max(255),
		kind: z.literal("text"),
	}),
	execute: async () => ({
		id: "",
		title: "",
		kind: "text" as const,
		content: "",
	}),
});

const updateDocumentTypeHelper = tool({
	description: "type helper",
	inputSchema: z.object({
		id: z.string().uuid(),
		description: z.string(),
	}),
	execute: async ({ id }) => ({
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
export type CreateDocumentInput = CreateDocumentTool["input"];
export type CreateDocumentOutput = CreateDocumentTool["output"];

export type UpdateDocumentTool = InferUITool<typeof updateDocumentTypeHelper>;
export type UpdateDocumentInput = UpdateDocumentTool["input"];
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

export type CustomUIDataTypes = {
	textDelta: string;
	appendMessage: string;
	id: string;
	title: string;
	kind: DocumentKind;
	clear: null;
	finish: null;
	usage: AppUsage;
	"document-create": DocumentCreateData;
	"document-update": DocumentUpdateData;
	"document-delta": DocumentDeltaData;
	"document-finish": DocumentFinishData;
};

export interface MessageMetadata {
	createdAt: string;
}

export type ChatMessage = UIMessage<
	MessageMetadata,
	CustomUIDataTypes,
	ChatTools
>;
