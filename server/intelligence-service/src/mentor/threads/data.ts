import { desc } from "drizzle-orm";
import db from "@/shared/db";
import { chatThread } from "@/shared/db/schema";

export type ThreadSummary = {
	id: string;
	title: string;
	createdAt?: string;
};

/**
 * Get all threads ordered by creation date (newest first).
 */
export async function getAllThreads(): Promise<ThreadSummary[]> {
	const rows = await db
		.select({
			id: chatThread.id,
			title: chatThread.title,
			createdAt: chatThread.createdAt,
		})
		.from(chatThread)
		.orderBy(desc(chatThread.createdAt));

	return rows.map((r) => ({
		id: r.id,
		title: r.title ?? "Untitled",
		createdAt: r.createdAt ?? undefined,
	}));
}
