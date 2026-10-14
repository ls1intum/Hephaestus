import { CalendarClockIcon, HandIcon, HistoryIcon, ZapIcon } from "lucide-react";

import type { ReviewRef } from "@/api/types.gen";

import type { StatusDefs } from "@/components/common/status-def";

export type ReviewOrigin = ReviewRef["origin"];

/**
 * What started a review — its "Started by" — in one set of words for every surface that says it: a
 * review, its results and the step in its trace. An event is the ordinary case; the other three
 * were self-selected — asked for by hand, run on a schedule, or made while catching up on work that
 * predates the connection — so reading one as a random draw from the work is the mistake the badge
 * prevents.
 */
export const REVIEW_ORIGIN_DEFS: StatusDefs<ReviewOrigin> = {
	EVENT: {
		label: "Event",
		icon: ZapIcon,
		badgeVariant: "outline",
		description: "Reviewed when something happened on the work.",
	},
	REQUEST: {
		label: "Requested",
		icon: HandIcon,
		badgeVariant: "outline",
		description: "Someone asked for this review.",
	},
	SCHEDULE: {
		label: "Scheduled",
		icon: CalendarClockIcon,
		badgeVariant: "outline",
		description: "A review schedule started this review.",
	},
	BACKFILL: {
		label: "Backfilled",
		icon: HistoryIcon,
		badgeVariant: "outline",
		description: "Reviewed while catching up on work that predates the connection.",
	},
};
