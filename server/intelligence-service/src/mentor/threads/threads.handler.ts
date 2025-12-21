import { ERROR_MESSAGES, HTTP_STATUS } from "@/shared/constants";
import type { AppRouteHandler } from "@/shared/http/types";
import { extractErrorMessage, getLogger } from "@/shared/utils";
import { getThreadsByUserAndWorkspace, type ThreadSummary } from "./data";
import type { HandleGetGroupedThreadsRoute } from "./threads.routes";

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
	const userId = c.get("userId");
	const workspaceId = c.get("workspaceId");

	// Require user context for authorization
	if (!(userId && workspaceId)) {
		return c.json(
			{ error: "Missing required context (userId or workspaceId)" },
			{ status: HTTP_STATUS.BAD_REQUEST },
		);
	}

	try {
		// Only fetch threads belonging to this user in this workspace
		const rows = await getThreadsByUserAndWorkspace(userId, workspaceId);

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
