import { desc } from "drizzle-orm";
import db from "@/db";
import { chatThread } from "@/db/schema";
import { ERROR_MESSAGES, HTTP_STATUS } from "@/lib/constants";
import type { AppRouteHandler } from "@/lib/types";
import type { HandleGetGroupedThreadsRoute } from "./threads.routes";

export const getGroupedThreadsHandler: AppRouteHandler<
	HandleGetGroupedThreadsRoute
> = async (c) => {
	const logger = c.get("logger");

	try {
		const rows = await db
			.select({
				id: chatThread.id,
				title: chatThread.title,
				createdAt: chatThread.createdAt,
			})
			.from(chatThread)
			.orderBy(desc(chatThread.createdAt));

		if (!rows.length) {
			return c.json([], { status: HTTP_STATUS.OK });
		}

		const now = new Date();
		const tzOffsetMs = now.getTimezoneOffset() * 60 * 1000;
		const today = new Date(now.getTime() - tzOffsetMs); // normalize to local day boundary basis
		const startOfDay = (d: Date) =>
			new Date(d.getFullYear(), d.getMonth(), d.getDate());

		const day0 = startOfDay(today).getTime();
		const day1 = new Date(startOfDay(today)).setDate(
			startOfDay(today).getDate() - 1,
		);
		const day7 = new Date(startOfDay(today)).setDate(
			startOfDay(today).getDate() - 7,
		);
		const day30 = new Date(startOfDay(today)).setDate(
			startOfDay(today).getDate() - 30,
		);
		const day90 = new Date(startOfDay(today)).setDate(
			startOfDay(today).getDate() - 90,
		);

		const groups: Record<
			string,
			{ id: string; title: string; createdAt?: string }[]
		> = {
			Today: [],
			Yesterday: [],
			"Last 7 Days": [],
			"Last 30 Days": [],
			"Last 90 Days": [],
			Older: [],
		};

		for (const r of rows) {
			const created = r.createdAt ? new Date(r.createdAt) : null;
			const createdMs = created ? startOfDay(created).getTime() : 0;

			let bucket: keyof typeof groups = "Older";
			if (createdMs >= day0) bucket = "Today";
			else if (createdMs >= day1) bucket = "Yesterday";
			else if (createdMs >= day7) bucket = "Last 7 Days";
			else if (createdMs >= day30) bucket = "Last 30 Days";
			else if (createdMs >= day90) bucket = "Last 90 Days";

			groups[bucket].push({
				id: r.id,
				title: r.title ?? "Untitled",
				createdAt: r.createdAt ?? undefined,
			});
		}

		const order: (keyof typeof groups)[] = [
			"Today",
			"Yesterday",
			"Last 7 Days",
			"Last 30 Days",
			"Last 90 Days",
			"Older",
		];

		const result = order
			.map((key) => ({ groupName: key, threads: groups[key] }))
			.filter((g) => g.threads.length > 0);

		return c.json(result, { status: HTTP_STATUS.OK });
	} catch (err) {
		logger.error({ err }, "Failed to fetch grouped threads");
		return c.json(
			{ error: ERROR_MESSAGES.INTERNAL_ERROR },
			{ status: HTTP_STATUS.INTERNAL_SERVER_ERROR },
		);
	}
};
