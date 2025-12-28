import { and, eq } from "drizzle-orm";
import db from "@/shared/db";
import { chatMessage, chatMessageVote, chatThread } from "@/shared/db/schema";

export type VoteRecord = {
	messageId: string;
	isUpvoted: boolean;
	createdAt: string;
	updatedAt: string;
};

/**
 * Check if a message exists and belongs to the user (via thread ownership).
 * Returns true only if the message exists and the user owns the thread.
 */
export async function messageExistsForUser(
	messageId: string,
	userId: number,
	workspaceId: number,
): Promise<boolean> {
	// Join message -> thread to verify ownership
	const rows = await db
		.select({ id: chatMessage.id })
		.from(chatMessage)
		.innerJoin(chatThread, eq(chatMessage.threadId, chatThread.id))
		.where(
			and(
				eq(chatMessage.id, messageId),
				eq(chatThread.userId, userId),
				eq(chatThread.workspaceId, workspaceId),
			),
		)
		.limit(1);
	return rows.length > 0;
}

/**
 * Get the current vote for a message.
 */
export async function getVoteByMessageId(messageId: string): Promise<VoteRecord | null> {
	const rows = await db
		.select()
		.from(chatMessageVote)
		.where(eq(chatMessageVote.messageId, messageId))
		.limit(1);

	const row = rows[0];
	if (!row) {
		return null;
	}

	return {
		messageId: row.messageId,
		isUpvoted: row.isUpvoted,
		createdAt: row.createdAt ?? new Date().toISOString(),
		updatedAt: row.updatedAt ?? new Date().toISOString(),
	};
}

/**
 * Create or update a vote for a message.
 */
export async function upsertVote(
	messageId: string,
	isUpvoted: boolean,
): Promise<VoteRecord | null> {
	const now = new Date().toISOString();
	const current = await getVoteByMessageId(messageId);

	if (current) {
		await db
			.update(chatMessageVote)
			.set({ isUpvoted, updatedAt: now })
			.where(eq(chatMessageVote.messageId, messageId));
	} else {
		await db.insert(chatMessageVote).values({
			messageId,
			isUpvoted,
			createdAt: now,
			updatedAt: now,
		});
	}

	return getVoteByMessageId(messageId);
}
