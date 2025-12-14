import type { InferUITool, LanguageModelUsage, UIMessage } from "ai";
import { tool } from "ai";
import { z } from "zod";

// Note: We define getWeather schemas here instead of importing from @/mentor/tools/weather.tool
// because this file is consumed by the webapp which cannot resolve the @/ path alias.
// Keep these schemas in sync with the actual tool definition.

// ─────────────────────────────────────────────────────────────────────────────
// Weather Tool Schemas (duplicated from get-weather.ts for cross-package compatibility)
// ─────────────────────────────────────────────────────────────────────────────

export const getWeatherInputSchema = z.object({
	latitude: z.number(),
	longitude: z.number(),
});

// Weather API response schema - matches Open-Meteo API response structure
// Using passthrough() to allow additional fields from the API we don't explicitly define
const weatherSuccessSchema = z
	.object({
		success: z.literal(true),
		latitude: z.number().optional(),
		longitude: z.number().optional(),
		generationtime_ms: z.number().optional(),
		utc_offset_seconds: z.number().optional(),
		timezone: z.string().optional(),
		timezone_abbreviation: z.string().optional(),
		elevation: z.number().optional(),
		current_units: z
			.object({
				time: z.string(),
				interval: z.string(),
				temperature_2m: z.string(),
			})
			.passthrough()
			.optional(),
		current: z
			.object({
				time: z.string(),
				interval: z.number(),
				temperature_2m: z.number(),
			})
			.passthrough()
			.optional(),
		hourly_units: z
			.object({
				time: z.string(),
				temperature_2m: z.string(),
			})
			.passthrough()
			.optional(),
		hourly: z
			.object({
				time: z.array(z.string()),
				temperature_2m: z.array(z.number()),
			})
			.passthrough()
			.optional(),
		daily_units: z
			.object({
				time: z.string(),
				sunrise: z.string(),
				sunset: z.string(),
			})
			.passthrough()
			.optional(),
		daily: z
			.object({
				time: z.array(z.string()).optional(),
				sunrise: z.array(z.string()),
				sunset: z.array(z.string()),
			})
			.passthrough()
			.optional(),
	})
	.passthrough();

const weatherErrorSchema = z.object({
	success: z.literal(false),
	error: z.string(),
});

export const getWeatherOutputSchema = z.discriminatedUnion("success", [
	weatherSuccessSchema,
	weatherErrorSchema,
]);

export type GetWeatherInput = z.infer<typeof getWeatherInputSchema>;
export type GetWeatherOutput = z.infer<typeof getWeatherOutputSchema>;

/**
 * Safely parse and validate getWeather output.
 * Returns the typed output or undefined if invalid.
 */
export function parseGetWeatherOutput(output: unknown): GetWeatherOutput | undefined {
	const result = getWeatherOutputSchema.safeParse(output);
	return result.success ? result.data : undefined;
}

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

const getWeatherTypeHelper = tool({
	description: "type helper",
	inputSchema: getWeatherInputSchema,
	execute: async () => ({ success: true as const }) as z.infer<typeof getWeatherOutputSchema>,
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

export type GetWeatherTool = InferUITool<typeof getWeatherTypeHelper>;

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
