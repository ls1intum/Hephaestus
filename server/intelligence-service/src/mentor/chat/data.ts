import { asc, desc, eq, inArray } from "drizzle-orm";
import db from "@/shared/db";
import { chatMessage, chatMessagePart, chatThread } from "@/shared/db/schema";

export type PersistedPart = {
	type: string;
	content: unknown;
	originalType?: string | null;
};

export type PersistedMessage = {
	id: string;
	role: "user" | "assistant" | "system";
	createdAt: Date;
	parts: PersistedPart[];
	parentMessageId?: string | null;
	threadId: string;
};

export async function getThreadById(id: string) {
	const rows = await db.select().from(chatThread).where(eq(chatThread.id, id)).limit(1);
	const row = Array.isArray(rows) ? rows[0] : undefined;
	return row ?? null;
}

export async function createThread(params: {
	id: string;
	title?: string | null;
	userId?: number | null;
	workspaceId: number;
}) {
	const now = new Date();
	const rows = await db
		.insert(chatThread)
		.values({
			id: params.id,
			createdAt: now.toISOString(),
			title: params.title ?? null,
			userId: params.userId ?? null,
			workspaceId: params.workspaceId,
		})
		.returning();
	return Array.isArray(rows) ? rows[0] : undefined;
}

export async function updateThreadTitle(id: string, title: string) {
	await db.update(chatThread).set({ title }).where(eq(chatThread.id, id));
}

export async function updateSelectedLeafMessageId(threadId: string, messageId: string) {
	await db
		.update(chatThread)
		.set({ selectedLeafMessageId: messageId })
		.where(eq(chatThread.id, threadId));
}

export async function getLastMessageId(threadId: string): Promise<string | null> {
	const rows = await db
		.select({ id: chatMessage.id })
		.from(chatMessage)
		.where(eq(chatMessage.threadId, threadId))
		.orderBy(desc(chatMessage.createdAt))
		.limit(1);
	return rows[0]?.id ?? null;
}

export async function getMessagesByThreadId(threadId: string): Promise<PersistedMessage[]> {
	// Fetch messages
	const messages = await db
		.select()
		.from(chatMessage)
		.where(eq(chatMessage.threadId, threadId))
		.orderBy(asc(chatMessage.createdAt));

	if (messages.length === 0) {
		return [];
	}

	// Fetch parts for all messages in one go
	const partRows = await db
		.select()
		.from(chatMessagePart)
		.where(
			inArray(
				chatMessagePart.messageId,
				messages.map((m) => m.id),
			),
		)
		.orderBy(asc(chatMessagePart.orderIndex));

	const partMap = new Map<string, PersistedPart[]>();
	for (const p of partRows) {
		const list = partMap.get(p.messageId) ?? [];
		list.push({
			type: p.type,
			originalType: p.originalType ?? null,
			content: (p.content as unknown) ?? null,
		});
		partMap.set(p.messageId, list);
	}

	return messages.map((m) => ({
		id: m.id,
		role: (m.role as PersistedMessage["role"]) ?? "assistant",
		createdAt: new Date(m.createdAt as string),
		parentMessageId: (m.parentMessageId as string | null) ?? null,
		threadId: m.threadId as string,
		parts: partMap.get(m.id) ?? [],
	}));
}

export async function saveMessage(params: {
	id: string;
	role: "user" | "assistant" | "system";
	threadId: string;
	parts: PersistedPart[];
	parentMessageId?: string | null;
	createdAt?: Date;
}) {
	const now = (params.createdAt ?? new Date()).toISOString();

	await db.insert(chatMessage).values({
		id: params.id,
		role: params.role,
		createdAt: now,
		threadId: params.threadId,
		parentMessageId: params.parentMessageId ?? null,
	});

	if (params.parts?.length > 0) {
		await db.insert(chatMessagePart).values(
			params.parts.map((part, idx) => ({
				messageId: params.id,
				orderIndex: idx,
				type: part.type,
				originalType: part.originalType ?? null,
				content: part.content as unknown,
			})),
		);
	}

	return params.id;
}
