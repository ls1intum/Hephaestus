/**
 * Shared types and schemas for cross-package compatibility.
 *
 * This file is consumed by both:
 * - intelligence-service (server)
 * - webapp (via @intelligence-service/chat/chat.shared alias)
 *
 * The webapp imports types from here for AI SDK UI components (useChat, etc.)
 * because it needs access to tool schemas and custom data types for proper
 * type inference in React components.
 *
 * DO NOT use @/ path aliases in this file - they won't resolve in the webapp.
 * Keep schemas in sync with the actual tool implementations.
 */

import type { InferUITool, LanguageModelUsage, UIMessage } from "ai";
import { tool } from "ai";
import { z } from "zod";

// ─────────────────────────────────────────────────────────────────────────────
// Document Input Schemas (Zod)
// ─────────────────────────────────────────────────────────────────────────────

export const createDocumentInputSchema = z.object({
	title: z.string().min(1).max(255),
	kind: z.literal("text"),
});

export const updateDocumentInputSchema = z.object({
	id: z.string().uuid(),
	description: z.string(),
});

// ─────────────────────────────────────────────────────────────────────────────
// Output Schemas (Zod)
// These match the execute() return types for runtime validation on the frontend
// ─────────────────────────────────────────────────────────────────────────────

export const createDocumentOutputSchema = z.object({
	id: z.string(),
	title: z.string(),
	kind: z.literal("text"),
	content: z.string(),
});

export const updateDocumentOutputSchema = z.object({
	id: z.string(),
	title: z.string(),
	kind: z.literal("text"),
	content: z.string(),
	description: z.string(),
});

// ─────────────────────────────────────────────────────────────────────────────
// Type Helpers (for InferUITool)
// ─────────────────────────────────────────────────────────────────────────────

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

export type CreateDocumentTool = InferUITool<typeof createDocumentTypeHelper>;
export type CreateDocumentInput = z.infer<typeof createDocumentInputSchema>;
export type CreateDocumentOutput = z.infer<typeof createDocumentOutputSchema>;

export type UpdateDocumentTool = InferUITool<typeof updateDocumentTypeHelper>;
export type UpdateDocumentInput = z.infer<typeof updateDocumentInputSchema>;
export type UpdateDocumentOutput = z.infer<typeof updateDocumentOutputSchema>;

// ─────────────────────────────────────────────────────────────────────────────
// Type Guards & Parsers
// Use these for runtime validation instead of `as` type assertions
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Safely parse and validate createDocument input.
 * Returns the typed input or undefined if invalid.
 */
export function parseCreateDocumentInput(input: unknown): CreateDocumentInput | undefined {
	const result = createDocumentInputSchema.safeParse(input);
	return result.success ? result.data : undefined;
}

/**
 * Safely parse and validate createDocument output.
 * Returns the typed output or undefined if invalid.
 */
export function parseCreateDocumentOutput(output: unknown): CreateDocumentOutput | undefined {
	const result = createDocumentOutputSchema.safeParse(output);
	return result.success ? result.data : undefined;
}

/**
 * Safely parse and validate updateDocument input.
 * Returns the typed input or undefined if invalid.
 */
export function parseUpdateDocumentInput(input: unknown): UpdateDocumentInput | undefined {
	const result = updateDocumentInputSchema.safeParse(input);
	return result.success ? result.data : undefined;
}

/**
 * Safely parse and validate updateDocument output.
 * Returns the typed output or undefined if invalid.
 */
export function parseUpdateDocumentOutput(output: unknown): UpdateDocumentOutput | undefined {
	const result = updateDocumentOutputSchema.safeParse(output);
	return result.success ? result.data : undefined;
}

/**
 * Type guard for checking if an unknown value has an id property.
 * Useful as a fallback when parsing fails but we need to extract the document ID.
 */
export function hasDocumentId(value: unknown): value is { id: string } & Record<string, unknown> {
	return (
		typeof value === "object" &&
		value !== null &&
		"id" in value &&
		typeof (value as { id: unknown }).id === "string"
	);
}

export type ChatTools = {
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
	usage: LanguageModelUsage & { modelId?: string };
};

/**
 * Document-specific data types (excludes usage).
 * Use this for handlers that only care about document streaming events.
 */
export type DocumentDataTypes = Omit<CustomUIDataTypes, "usage">;

export interface MessageMetadata {
	createdAt: string;
}

export type ChatMessage = UIMessage<MessageMetadata, CustomUIDataTypes, ChatTools>;
