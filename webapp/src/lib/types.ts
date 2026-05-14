import type { UIMessage } from "ai";

/**
 * Custom UI data types streamed by the Pi mentor.
 *
 * The legacy intelligence-service produced typed document streaming events
 * (data-document-create / -update / -delta / -finish). The Pi mentor does not
 * emit those today — the only custom data part is `data-usage` for token
 * accounting, which the client currently ignores.
 *
 * Keep this open enough to absorb future server-side additions without
 * coupling the webapp to a generated TypeScript schema.
 */
export type CustomUIDataTypes = Record<string, unknown>;

/**
 * Message metadata attached to mentor messages.
 *
 * The server may attach a `createdAt` timestamp on each ChatMessage record.
 * Other fields are reserved for future use.
 */
export interface MessageMetadata {
	createdAt?: string;
}

/**
 * Tool registry placeholder.
 *
 * The Pi mentor surface currently has no client-rendered tools. When tools
 * are reintroduced, declare them here (`{ toolName: { input, output } }`)
 * and the renderer map will pick them up automatically.
 */
export type ChatTools = Record<string, { input: unknown; output: unknown }>;

/**
 * Chat message type for the mentor surface.
 *
 * Aligns with AI SDK's UIMessage so `useChat<ChatMessage>` and
 * `readUIMessageStream` consume it correctly. Runtime validation lives in
 * `lib/chat-validation.ts`.
 */
export type ChatMessage = UIMessage<MessageMetadata, CustomUIDataTypes, ChatTools>;

export type DataPart = never;

// Artifact typing
export type ArtifactKind = "text" | (string & {});
export type ArtifactId<K extends ArtifactKind = ArtifactKind> = `${K}:${string}`;

export function makeArtifactId<K extends ArtifactKind>(kind: K, payload: string): ArtifactId<K> {
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
