import type {
	DocumentCreateData,
	DocumentDeltaData,
	DocumentFinishData,
	DocumentUpdateData,
} from "@intelligence-service/chat/chat.shared";
import { z } from "zod";

export type {
	ChatMessage,
	ChatTools,
	CreateDocumentInput,
	CreateDocumentOutput,
	DocumentCreateData,
	DocumentDeltaData,
	DocumentFinishData,
	DocumentUpdateData,
	GetWeatherInput,
	GetWeatherOutput,
	MessageMetadata,
	UpdateDocumentInput,
	UpdateDocumentOutput,
} from "@intelligence-service/chat/chat.shared";

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
	// We accept any string as extensible ArtifactKind (open union)
	return { kind: (k as ArtifactKind) ?? null, payload };
}

export const messageMetadataSchema = z.object({
	createdAt: z.string(),
});

/**
 * Union type for custom streaming data parts.
 * Maps CustomUIDataTypes keys to `{ type: "data-{key}", data: T[key] }` format.
 */
export type DataPart =
	| { type: "data-document-create"; data: DocumentCreateData }
	| { type: "data-document-update"; data: DocumentUpdateData }
	| { type: "data-document-delta"; data: DocumentDeltaData }
	| { type: "data-document-finish"; data: DocumentFinishData }
	| { type: "data-usage"; data: unknown };

// Typed tools mapping for automatic tool part typing in UIMessage

export interface Attachment {
	name: string;
	url: string;
	contentType: string;
}
