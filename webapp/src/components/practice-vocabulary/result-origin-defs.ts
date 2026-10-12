import { HandIcon, HistoryIcon, ZapIcon } from "lucide-react";

import type { PracticeResultDetail } from "@/api/types.gen";

import type { StatusDefs } from "@/components/common/status-def";

export type ResultOrigin = PracticeResultDetail["origin"];

/**
 * How a review that the work did not start came about, in one set of words for every surface that
 * says it: the origin of a result, the trigger of a review and the moment in its trace. A
 * rename changes it here, once.
 */
export const REVIEW_OCCASION_LABELS = {
	MANUAL: "Requested",
	BACKFILL: "Backfilled",
	SWEEP: "Scheduled check",
} as const;

/**
 * What occasioned the measurement. A live result is the ordinary case and a surface says
 * nothing for it; the other two were self-selected — asked for by hand, or made while catching up
 * on work that predates the connection — so reading either as a random draw from the work is the
 * mistake the badge prevents.
 */
export const RESULT_ORIGIN_DEFS: StatusDefs<ResultOrigin> = {
	LIVE: {
		label: "Live",
		icon: ZapIcon,
		badgeVariant: "outline",
		description: "Reviewed as the work arrived.",
	},
	MANUAL: {
		label: REVIEW_OCCASION_LABELS.MANUAL,
		icon: HandIcon,
		badgeVariant: "outline",
		description: "Someone asked for this review.",
	},
	BACKFILL: {
		label: REVIEW_OCCASION_LABELS.BACKFILL,
		icon: HistoryIcon,
		badgeVariant: "outline",
		description: "Reviewed while catching up on work that predates the connection.",
	},
};
