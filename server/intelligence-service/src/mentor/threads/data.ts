import { and, desc, eq } from "drizzle-orm";
import db from "@/shared/db";
import { chatThread } from "@/shared/db/schema";

export type ThreadSummary = {
	id: string;
	title: string;
	createdAt?: string;
};

/**
 * Get all threads for a specific user and workspace, ordered by creation date (newest first).
 * This ensures users only see their own threads within their authorized workspace.
 */
export async function getThreadsByUserAndWorkspace(
	userId: number,
	workspaceId: number,
): Promise<ThreadSummary[]> {
	const rows = await db
		.select({
			id: chatThread.id,
			title: chatThread.title,
			createdAt: chatThread.createdAt,
		})
		.from(chatThread)
		.where(and(eq(chatThread.userId, userId), eq(chatThread.workspaceId, workspaceId)))
		.orderBy(desc(chatThread.createdAt));

	return rows.map((r) => ({
		id: r.id,
		title: r.title ?? "Untitled",
		createdAt: r.createdAt ?? undefined,
	}));
}
