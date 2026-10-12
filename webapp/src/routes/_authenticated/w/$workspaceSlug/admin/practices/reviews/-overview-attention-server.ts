import { HttpResponse, http } from "msw";

import type { PracticeReviewOverview, PracticeReviewQueue } from "@/api/types.gen";
import {
	feedbackCounts,
	idleReviewQueue,
	practiceReviewOverview,
	practiceReviewTiming,
} from "@/components/admin/practice-reviews/fixtures";
import { reviewRangeStart } from "@/components/admin/practice-reviews/review-range";
import { reviewHandlers } from "@/components/admin/practice-reviews/story-mock-server";
import { server } from "@/mocks/server";

/**
 * The Practice reviews overview past its attention thresholds, for the route tests of its signals:
 * a range with 5 of 85 reviews failed, and a queue whose oldest has waited 42 minutes.
 */
export const OVERVIEW = "/w/acme/admin/practices/reviews";

/** The range: nothing queued or running, so nothing polls. */
export const rangeOverview: PracticeReviewOverview = {
	...practiceReviewOverview,
	reviews: { completed: 80, failed: 4, timedOut: 1, cancelled: 0, running: 0, queued: 0 },
	feedback: {
		...feedbackCounts([]),
		delivered: 60,
		prepared: 5,
		failed: 2,
		suppressed: 30,
		discarded: 3,
		superseded: 20,
	},
};

/** Fourteen waiting, the oldest for 42 minutes. */
function longWaitQueue(): PracticeReviewQueue {
	return {
		...idleReviewQueue,
		waiting: 14,
		oldestWaitingSince: new Date(idleReviewQueue.asOf.getTime() - 42 * 60_000),
	};
}

/**
 * `week`, given, answers the read of the fixed week, so a test can tell it from the range's read;
 * without it, every read gets the range.
 */
export function serveAttention(week?: PracticeReviewOverview) {
	const overviewReads: string[] = [];
	server.use(
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json({ role: "ADMIN", userId: 1, userLogin: "ada", userName: "Ada" }),
		),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/overview", ({ request }) => {
			const from = new URL(request.url).searchParams.get("from");
			overviewReads.push(from ?? "");
			const weekStart = reviewRangeStart(
				// oxlint-disable-next-line no-restricted-properties -- the route reads its week against the wall clock too.
				Date.now(),
				"7d",
			).getTime();
			const isWeek = from !== null && Date.parse(from) === weekStart;
			return HttpResponse.json(isWeek && week !== undefined ? week : rangeOverview);
		}),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/timing", () =>
			HttpResponse.json({
				...practiceReviewTiming,
				waitingLong: 9,
				feedbackWithheldAsMerged: 21,
			}),
		),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/queue", () =>
			HttpResponse.json(longWaitQueue()),
		),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback", () =>
			HttpResponse.json({
				content: [],
				page: { size: 1, number: 0, totalElements: 0, totalPages: 1 },
			}),
		),
		...reviewHandlers(),
	);
	return { overviewReads };
}
