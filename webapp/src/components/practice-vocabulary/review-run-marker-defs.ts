import { GitMergeIcon, PauseCircleIcon } from "lucide-react";

import type { StatusDefs } from "@/components/common/status-def";

/** A problem a review on the Reviews list is marked with beside its outcome. */
export type ReviewRunMarker = "ON_HOLD" | "FINISHED_AFTER_MERGE";

export const REVIEW_RUN_MARKER_DEFS: StatusDefs<ReviewRunMarker> = {
	ON_HOLD: {
		label: "On hold",
		icon: PauseCircleIcon,
		badgeVariant: "warning",
		description:
			"The review waits, for example because the AI budget is used up. It continues on its own when the hold ends.",
	},
	FINISHED_AFTER_MERGE: {
		label: "Finished after the merge",
		icon: GitMergeIcon,
		badgeVariant: "warning",
		description:
			"The work was merged before the review finished, so its feedback came too late to change the work.",
	},
};
