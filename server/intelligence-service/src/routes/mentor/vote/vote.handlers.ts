import { eq } from "drizzle-orm";
import db from "@/db";
import { chatMessage, chatMessageVote } from "@/db/schema";
import type { AppRouteHandler } from "@/lib/types";
import type { HandleVoteMessageRoute } from "./vote.routes";

export const voteMessageHandler: AppRouteHandler<
	HandleVoteMessageRoute
> = async (c) => {
	const logger = c.get("logger");
	const { messageId } = c.req.valid("param");
	const { isUpvoted } = c.req.valid("json");

	try {
		const existing = await db
			.select({ id: chatMessage.id })
			.from(chatMessage)
			.where(eq(chatMessage.id, messageId))
			.limit(1);
		if (!existing[0]) {
			return c.json({ error: "Message not found" }, { status: 404 });
		}

		const now = new Date().toISOString();
		// Upsert semantics: if exists update, else insert
		const current = await db
			.select()
			.from(chatMessageVote)
			.where(eq(chatMessageVote.messageId, messageId))
			.limit(1);

		if (current[0]) {
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

		const row = (
			await db
				.select()
				.from(chatMessageVote)
				.where(eq(chatMessageVote.messageId, messageId))
				.limit(1)
		)[0];

		if (!row) {
			return c.json({ error: "Vote retrieval failed" }, { status: 500 });
		}

		return c.json(
			{
				messageId: row.messageId,
				isUpvoted: row.isUpvoted,
				createdAt: row.createdAt ?? new Date().toISOString(),
				updatedAt: row.updatedAt ?? new Date().toISOString(),
			},
			{ status: 200 },
		);
	} catch (err) {
		logger.error({ err }, "Vote message failed");
		return c.json({ error: "Internal error" }, { status: 500 });
	}
};
