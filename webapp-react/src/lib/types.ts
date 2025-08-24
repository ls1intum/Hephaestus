import type { UIMessage } from "ai";
import { z } from "zod";
import type {
	CreateDocumentInput,
	CreateDocumentOutput,
	DocumentCreateData,
	DocumentDeltaData,
	DocumentFinishData,
	DocumentUpdateData,
	GetWeatherInput,
	GetWeatherOutput,
	UpdateDocumentInput,
	UpdateDocumentOutput,
} from "@/api/types.gen";

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

export type MessageMetadata = z.infer<typeof messageMetadataSchema>;

export type CustomUIDataTypes = {
	"document-create": DocumentCreateData;
	"document-update": DocumentUpdateData;
	"document-delta": DocumentDeltaData;
	"document-finish": DocumentFinishData;
};

type ToDataMessageUnion<T extends Record<PropertyKey, unknown>> = {
	[K in keyof T]: { type: `data-${Extract<K, string>}`; data: T[K] };
}[keyof T];

export type DataPart = ToDataMessageUnion<CustomUIDataTypes>;

// Typed tools mapping for automatic tool part typing in UIMessage
export type ChatTools = {
	getWeather: {
		input: GetWeatherInput;
		output: GetWeatherOutput;
	};
	createDocument: {
		input: CreateDocumentInput;
		output: CreateDocumentOutput;
	};
	updateDocument: {
		input: UpdateDocumentInput;
		output: UpdateDocumentOutput;
	};
};

export type ChatMessage = UIMessage<
	MessageMetadata,
	CustomUIDataTypes,
	ChatTools
>;

export interface Attachment {
	name: string;
	url: string;
	contentType: string;
}
