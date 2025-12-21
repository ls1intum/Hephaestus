import { getMessagesByThreadId, getThreadById } from "@/mentor/chat/data";
import { ERROR_MESSAGES, HTTP_STATUS } from "@/shared/constants";
import type { AppRouteHandler } from "@/shared/http/types";
import { getValidatedContext } from "@/shared/http/workspace-context";
import { extractErrorMessage, getLogger } from "@/shared/utils";
import { getThreadsByUserAndWorkspace, type ThreadSummary } from "./data";
import type { HandleGetGroupedThreadsRoute, HandleGetThreadRoute } from "./threads.routes";
import type { ThreadDetail } from "./threads.schema";

type ThreadRow = ThreadSummary;

const GROUP_ORDER = [
	"Today",
	"Yesterday",
	"Last 7 Days",
	"Last 30 Days",
	"Last 90 Days",
	"Older",
] as const;

type GroupName = (typeof GROUP_ORDER)[number];

function startOfLocalDayMs(date: Date): number {
	return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
}

function addDays(dateMs: number, days: number): number {
	const d = new Date(dateMs);
	d.setDate(d.getDate() + days);
	return d.getTime();
}

function groupThreadsByRecency(rows: ThreadRow[], now: Date) {
	const todayStartMs = startOfLocalDayMs(now);
	const thresholds: Record<Exclude<GroupName, "Older">, number> = {
		Today: todayStartMs,
		Yesterday: addDays(todayStartMs, -1),
		"Last 7 Days": addDays(todayStartMs, -7),
		"Last 30 Days": addDays(todayStartMs, -30),
		"Last 90 Days": addDays(todayStartMs, -90),
	};

	const groups: Record<GroupName, ThreadSummary[]> = {
		Today: [],
		Yesterday: [],
		"Last 7 Days": [],
		"Last 30 Days": [],
		"Last 90 Days": [],
		Older: [],
	};

	for (const row of rows) {
		const createdMs = row.createdAt
			? startOfLocalDayMs(new Date(row.createdAt))
			: Number.NEGATIVE_INFINITY;

		let bucket: GroupName = "Older";
		for (const name of GROUP_ORDER) {
			if (name === "Older") {
				break;
			}
			if (createdMs >= thresholds[name]) {
				bucket = name;
				break;
			}
		}

		groups[bucket].push({ id: row.id, title: row.title, createdAt: row.createdAt });
	}

	return GROUP_ORDER.map((key) => ({ groupName: key, threads: groups[key] })).filter(
		(g) => g.threads.length > 0,
	);
}

export const getGroupedThreadsHandler: AppRouteHandler<HandleGetGroupedThreadsRoute> = async (
	c,
) => {
	const logger = getLogger(c);

	const ctx = getValidatedContext(c);
	if (!ctx) {
		return c.json({ error: ERROR_MESSAGES.MISSING_CONTEXT }, { status: HTTP_STATUS.BAD_REQUEST });
	}

	try {
		// Only fetch threads belonging to this user in this workspace
		const rows = await getThreadsByUserAndWorkspace(ctx.userId, ctx.workspaceId);

		if (rows.length === 0) {
			return c.json([], { status: HTTP_STATUS.OK });
		}

		return c.json(groupThreadsByRecency(rows, new Date()), { status: HTTP_STATUS.OK });
	} catch (err) {
		logger.error({ err: extractErrorMessage(err) }, "Failed to fetch grouped threads");
		return c.json(
			{ error: ERROR_MESSAGES.INTERNAL_ERROR },
			{ status: HTTP_STATUS.INTERNAL_SERVER_ERROR },
		);
	}
};

// ─────────────────────────────────────────────────────────────────────────────
// Get Thread Detail Handler
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Transform persisted message to API response format.
 */
function toThreadDetailMessage(msg: {
	id: string;
	role: string;
	parts: Array<{ type: string; content: unknown; originalType?: string | null }>;
	createdAt: Date;
	parentMessageId?: string | null;
}) {
	return {
		id: msg.id,
		role: msg.role as "system" | "user" | "assistant",
		parts: msg.parts.map((part) => {
			const content = part.content as Record<string, unknown>;
			return { type: part.originalType ?? part.type, ...content };
		}),
		createdAt: msg.createdAt.toISOString(),
		parentMessageId: msg.parentMessageId ?? null,
	};
}

export const getThreadHandler: AppRouteHandler<HandleGetThreadRoute> = async (c) => {
	const logger = getLogger(c);
	const { threadId } = c.req.valid("param");

	const ctx = getValidatedContext(c);
	if (!ctx) {
		return c.json({ error: ERROR_MESSAGES.MISSING_CONTEXT }, { status: HTTP_STATUS.BAD_REQUEST });
	}

	try {
		const thread = await getThreadById(threadId);

		// Check if thread exists
		if (!thread) {
			return c.json({ error: ERROR_MESSAGES.THREAD_NOT_FOUND }, { status: HTTP_STATUS.NOT_FOUND });
		}

		// Check ownership - thread must belong to the same user and workspace
		// Note: thread.userId can be null for legacy threads created before auth was added
		const threadUserId = thread.userId ? Number(thread.userId) : null;
		const threadWorkspaceId = thread.workspaceId ? Number(thread.workspaceId) : null;

		if (threadUserId !== ctx.userId || threadWorkspaceId !== ctx.workspaceId) {
			logger.debug(
				{
					threadId,
					threadUserId,
					userId: ctx.userId,
					threadWorkspaceId,
					workspaceId: ctx.workspaceId,
				},
				"Thread ownership mismatch",
			);
			return c.json({ error: ERROR_MESSAGES.THREAD_NOT_FOUND }, { status: HTTP_STATUS.NOT_FOUND });
		}

		const history = await getMessagesByThreadId(threadId);
		const messages = history.map(toThreadDetailMessage);

		const response: ThreadDetail = {
			id: thread.id,
			title: thread.title ?? null,
			selectedLeafMessageId: thread.selectedLeafMessageId ?? null,
			messages,
		};

		return c.json(response, { status: HTTP_STATUS.OK });
	} catch (err) {
		logger.error({ err: extractErrorMessage(err) }, "Failed to fetch thread");
		return c.json(
			{ error: ERROR_MESSAGES.SERVICE_UNAVAILABLE },
			{ status: HTTP_STATUS.SERVICE_UNAVAILABLE },
		);
	}
};
