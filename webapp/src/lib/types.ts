import type { CustomUIDataTypes } from "@intelligence-service/chat/chat.shared";
import type { DataUIPart } from "ai";

// Re-export types from intelligence-service that are used for AI SDK chat UI
// These are different from the simple API types in @/api/types.gen
export type {
	// Chat message with AI SDK UI parts (tool invocations, reasoning, etc.)
	ChatMessage,
	// Tool type definitions for typed tool parts
	ChatTools,
	// Tool input/output types
	CreateDocumentInput,
	CreateDocumentOutput,
	// Custom data types registry
	CustomUIDataTypes,
	// Streaming data types for custom document operations
	DocumentCreateData,
	// Document-specific data types (for handlers that only care about documents)
	DocumentDataTypes,
	DocumentDeltaData,
	DocumentFinishData,
	DocumentUpdateData,
	GetWeatherInput,
	GetWeatherOutput,
	// Message metadata
	MessageMetadata,
	UpdateDocumentInput,
	UpdateDocumentOutput,
} from "@intelligence-service/chat/chat.shared";

// Re-export type guards and parsers for runtime validation (AI SDK v6 best practice)
export {
	hasDocumentId,
	parseCreateDocumentInput,
	parseCreateDocumentOutput,
	parseGetWeatherOutput,
	parseUpdateDocumentInput,
	parseUpdateDocumentOutput,
} from "@intelligence-service/chat/chat.shared";

/**
 * Type-safe data part derived from AI SDK's DataUIPart.
 * Automatically includes all CustomUIDataTypes with proper `data-` prefixes.
 */
export type DataPart = DataUIPart<CustomUIDataTypes>;

// Artifact typing
export type ArtifactKind = "text" | (string & {});
export type ArtifactId<K extends ArtifactKind = ArtifactKind> =
	`${K}:${string}`;

export function makeArtifactId<K extends ArtifactKind>(
	kind: K,
	payload: string,
): ArtifactId<K> {
	return `${kind}:${payload}` as ArtifactId<K>;
}

export function parseArtifactId(id: string | null | undefined): {
	kind: ArtifactKind | null;
	payload: string | null;
} {
	if (!id) return { kind: null, payload: null };
	const [k, ...rest] = id.split(":");
	const payload = rest.length > 0 ? rest.join(":") : null;
	return { kind: (k as ArtifactKind) ?? null, payload };
}

export interface Attachment {
	name: string;
	url: string;
	contentType: string;
}
