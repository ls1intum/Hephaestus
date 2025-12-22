/**
 * Chat message type transformations.
 *
 * This module handles all conversions between:
 * - Incoming API messages (IncomingMessage)
 * - Persisted database records (PersistedMessage)
 * - AI SDK UIMessage format (UMessage)
 * - API response format (ThreadDetail)
 */

import type { UIMessage } from "ai";
import type { ThreadDetail } from "@/mentor/threads/threads.schema";
import type { PersistedMessage, PersistedPart } from "./data";

// ─────────────────────────────────────────────────────────────────────────────
// Internal Types
// ─────────────────────────────────────────────────────────────────────────────

type UTextPart = { type: "text"; text: string };
type UFilePart = {
	type: "file";
	url: string;
	mediaType: "image/jpeg" | "image/png";
	name?: string;
};

export type UMessage = UIMessage<
	Record<string, never>,
	Record<string, never>,
	Record<string, never>
>;

export type IncomingPart = { type: string; [k: string]: unknown };

// Re-export PersistedPart from data module for test imports
export type { PersistedPart } from "./data";

// ─────────────────────────────────────────────────────────────────────────────
// Type Guards
// ─────────────────────────────────────────────────────────────────────────────

function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === "object" && value !== null;
}

function isValidMediaType(value: unknown): value is "image/jpeg" | "image/png" {
	return value === "image/jpeg" || value === "image/png";
}

// ─────────────────────────────────────────────────────────────────────────────
// Persisted → UI Message Conversion
// ─────────────────────────────────────────────────────────────────────────────

function extractTextPart(content: unknown): UTextPart | null {
	if (!isRecord(content)) {
		return null;
	}
	if (typeof content.text === "string") {
		return { type: "text", text: content.text };
	}
	return null;
}

function extractFilePart(content: unknown): UFilePart | null {
	if (!isRecord(content)) {
		return null;
	}
	if (typeof content.url === "string" && isValidMediaType(content.mediaType)) {
		return {
			type: "file",
			url: content.url,
			mediaType: content.mediaType,
			name: typeof content.name === "string" ? content.name : undefined,
		};
	}
	return null;
}

/**
 * Convert persisted parts to UI-compatible parts.
 */
export function uiPartsFromPersisted(parts: PersistedPart[]): Array<UTextPart | UFilePart> {
	const result: Array<UTextPart | UFilePart> = [];

	for (const part of parts) {
		if (part.type === "text") {
			const textPart = extractTextPart(part.content);
			if (textPart) {
				result.push(textPart);
			}
		} else if (part.type === "file") {
			const filePart = extractFilePart(part.content);
			if (filePart) {
				result.push(filePart);
			}
		}
	}

	return result;
}

/**
 * Convert a persisted message to AI SDK UIMessage format.
 */
export function uiMessageFromPersisted(message: PersistedMessage): UMessage {
	return {
		id: message.id,
		role: message.role,
		parts: uiPartsFromPersisted(message.parts),
	} as UMessage;
}

// ─────────────────────────────────────────────────────────────────────────────
// Persisted → Thread Detail Conversion (API Response)
// ─────────────────────────────────────────────────────────────────────────────

type ThreadPart = ThreadDetail["messages"][number]["parts"][number];

function coerceToThreadPart(content: unknown): ThreadPart {
	if (!isRecord(content)) {
		return { type: "data-unknown", value: content };
	}

	if (typeof content.type !== "string") {
		return { type: "data-unknown", value: content };
	}

	// File parts need special handling for media type validation
	if (content.type === "file") {
		const filePart = extractFilePart(content);
		if (filePart) {
			return filePart;
		}
		return { type: "data-file", file: content };
	}

	// Other parts pass through - ThreadPart is a union with passthrough,
	// so any object with a string `type` is valid
	return content as ThreadPart;
}

/**
 * Convert a persisted message to API response format.
 */
export function toThreadDetailMessage(message: PersistedMessage): ThreadDetail["messages"][number] {
	const parts = message.parts
		.map((p) => p.content)
		.filter((c): c is NonNullable<typeof c> => c != null)
		.map(coerceToThreadPart) as ThreadDetail["messages"][number]["parts"];

	return {
		id: message.id,
		role: message.role,
		parts,
		createdAt: message.createdAt.toISOString(),
		parentMessageId: message.parentMessageId ?? null,
	};
}

// ─────────────────────────────────────────────────────────────────────────────
// Incoming → Persisted Conversion
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Convert incoming message parts to persistence format.
 * Filters out ephemeral data-* parts that shouldn't be saved.
 */
export function partsToPersist(parts: IncomingPart[]): PersistedPart[] {
	return parts
		.filter((p) => {
			// Drop ephemeral control/data events from persistence
			if (typeof p.type === "string" && p.type.startsWith("data-")) {
				return false;
			}
			return true;
		})
		.map((p) => {
			if (p.type === "text" && typeof p.text === "string") {
				return {
					type: "text",
					originalType: "text",
					content: { type: "text", text: p.text },
				};
			}

			if (p.type === "file" && typeof p.url === "string" && typeof p.mediaType === "string") {
				return {
					type: "file",
					originalType: "file",
					content: {
						type: "file",
						url: p.url,
						mediaType: p.mediaType,
						name: typeof p.name === "string" ? p.name : undefined,
						providerMetadata: isRecord(p.providerMetadata) ? p.providerMetadata : undefined,
					},
				};
			}

			// For reasoning, tool parts, and others - store as-is
			return {
				type: p.type,
				originalType: p.type,
				content: p,
			};
		});
}

// ─────────────────────────────────────────────────────────────────────────────
// Incoming → UI Message Conversion
// ─────────────────────────────────────────────────────────────────────────────

export type IncomingMessage = {
	id: string;
	role: "user";
	parts: Array<
		{ type: "text"; text: string } | { type: "file"; url: string; mediaType: string; name?: string }
	>;
};

/**
 * Convert an incoming user message to AI SDK UIMessage format.
 *
 * Note: The `as UMessage` cast is required because UIMessage has complex
 * generic parameters that don't match our simplified message structure.
 * The runtime types are compatible.
 */
export function incomingToUiMessage(message: IncomingMessage): UMessage {
	const parts: Array<UTextPart | UFilePart> = message.parts.map((p) => {
		if (p.type === "text") {
			return { type: "text", text: p.text };
		}
		// Validate mediaType at runtime, default to image/jpeg if invalid
		const mediaType = isValidMediaType(p.mediaType) ? p.mediaType : "image/jpeg";
		return {
			type: "file",
			url: p.url,
			mediaType,
			name: p.name,
		};
	});

	// Cast required: UIMessage generic parameters don't match our simplified types
	return { id: message.id, role: "user", parts } as UMessage;
}

/**
 * Infer a thread title from the first text part of a message.
 */
export function inferTitleFromMessage(message: IncomingMessage): string {
	const firstText = message.parts.find(
		(p): p is { type: "text"; text: string } => p.type === "text",
	);
	const raw = (firstText?.text ?? "").trim() || "New chat";
	return raw.length > 60 ? `${raw.slice(0, 57)}...` : raw;
}
