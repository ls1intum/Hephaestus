/**
 * Test Builders - Eliminate Duplication
 *
 * Factory functions for creating test data with sensible defaults.
 * All IDs are tracked for automatic cleanup.
 */

import { v4 as uuidv4 } from "uuid";
import type { PersistedPart } from "@/mentor/chat/data";
import { createThread, saveMessage } from "@/mentor/chat/data";

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

export interface ThreadBuilder {
	id: string;
	workspaceId: number;
	title?: string | null;
	userId?: number | null;
}

export interface MessageBuilder {
	id: string;
	threadId: string;
	role: "user" | "assistant" | "system";
	parts: PersistedPart[];
	parentMessageId?: string | null;
	createdAt?: Date;
}

// ─────────────────────────────────────────────────────────────────────────────
// ID Tracking
// ─────────────────────────────────────────────────────────────────────────────

const trackedThreads: string[] = [];

/** Get all tracked thread IDs for cleanup. */
export function getTrackedThreads(): readonly string[] {
	return trackedThreads;
}

/** Clear tracked threads after cleanup. */
export function clearTrackedThreads(): void {
	trackedThreads.length = 0;
}

/** Track a thread ID for later cleanup. */
function track(id: string): string {
	trackedThreads.push(id);
	return id;
}

// ─────────────────────────────────────────────────────────────────────────────
// Thread Factory
// ─────────────────────────────────────────────────────────────────────────────

interface CreateThreadOptions {
	id?: string;
	title?: string;
	userId?: number;
}

/**
 * Create a thread with sensible defaults. Automatically tracked for cleanup.
 *
 * @example
 * const threadId = await createTestThread(fixtures.workspace.id);
 * const threadId = await createTestThread(fixtures.workspace.id, { title: 'My Chat' });
 */
export async function createTestThread(
	workspaceId: number,
	options: CreateThreadOptions = {},
): Promise<string> {
	const id = options.id ?? uuidv4();
	track(id);

	await createThread({
		id,
		workspaceId,
		title: options.title ?? null,
		userId: options.userId ?? null,
	});

	return id;
}

// ─────────────────────────────────────────────────────────────────────────────
// Message Factory
// ─────────────────────────────────────────────────────────────────────────────

interface CreateMessageOptions {
	id?: string;
	role?: "user" | "assistant" | "system";
	text?: string;
	parts?: PersistedPart[];
	parentMessageId?: string;
	createdAt?: Date;
}

/**
 * Create a message with sensible defaults.
 *
 * @example
 * await createTestMessage(threadId, { text: 'Hello' });
 * await createTestMessage(threadId, { role: 'assistant', text: 'Hi there!' });
 */
export async function createTestMessage(
	threadId: string,
	options: CreateMessageOptions = {},
): Promise<string> {
	const id = options.id ?? uuidv4();

	const parts: PersistedPart[] = options.parts ?? [
		{ type: "text", content: { text: options.text ?? "Test message" } },
	];

	await saveMessage({
		id,
		threadId,
		role: options.role ?? "user",
		parts,
		parentMessageId: options.parentMessageId ?? null,
		createdAt: options.createdAt,
	});

	return id;
}

// ─────────────────────────────────────────────────────────────────────────────
// Conversation Builder
// ─────────────────────────────────────────────────────────────────────────────

interface ConversationTurn {
	role: "user" | "assistant";
	text: string;
}

/**
 * Create a complete conversation with multiple turns.
 *
 * @example
 * const { threadId, messageIds } = await createConversation(workspaceId, [
 *   { role: 'user', text: 'Hello' },
 *   { role: 'assistant', text: 'Hi there!' },
 *   { role: 'user', text: 'How are you?' },
 * ]);
 */
export async function createConversation(
	workspaceId: number,
	turns: ConversationTurn[],
): Promise<{ threadId: string; messageIds: string[] }> {
	const threadId = await createTestThread(workspaceId);
	const messageIds: string[] = [];

	let parentId: string | undefined;

	for (let i = 0; i < turns.length; i++) {
		const turn = turns[i];
		if (!turn) {
			continue;
		}

		const msgId = await createTestMessage(threadId, {
			role: turn.role,
			text: turn.text,
			parentMessageId: parentId,
			createdAt: new Date(Date.now() + i * 1000), // Ensure ordering
		});

		messageIds.push(msgId);
		parentId = msgId;
	}

	return { threadId, messageIds };
}

// ─────────────────────────────────────────────────────────────────────────────
// Part Builders
// ─────────────────────────────────────────────────────────────────────────────

/** Create a text part. */
export function textPart(text: string): PersistedPart {
	return { type: "text", content: { text } };
}

/** Create a reasoning part. */
export function reasoningPart(text: string): PersistedPart {
	return { type: "reasoning", content: { text } };
}

/** Create a file part. */
export function filePart(url: string, mediaType: string, name?: string): PersistedPart {
	return { type: "file", content: { url, mediaType, name } };
}

/** Create a tool call part. */
export function toolCallPart(toolName: string, args: Record<string, unknown>): PersistedPart {
	return { type: "tool-invocation", content: { toolName, args, state: "result" } };
}
