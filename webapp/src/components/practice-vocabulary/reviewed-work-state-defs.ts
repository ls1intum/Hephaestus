import {
	BanIcon,
	CircleCheckIcon,
	CircleDotIcon,
	CircleMinusIcon,
	CircleOffIcon,
	CircleSlashIcon,
	CircleXIcon,
	ClockIcon,
	GitMergeIcon,
	SkipForwardIcon,
} from "lucide-react";

import type { ReviewWorkFacts } from "@/api/types.gen";

import type { StatusDefs } from "@/components/common/status-def";

export type ReviewedWorkState = ReviewWorkFacts["state"];
export type ReviewedWorkCheckState = NonNullable<ReviewWorkFacts["checkState"]>;

/** Where a pull request, merge request or issue stands on its provider. */
export const REVIEWED_WORK_STATE_DEFS: StatusDefs<ReviewedWorkState> = {
	OPEN: {
		label: "Open",
		icon: CircleDotIcon,
		badgeVariant: "outline",
		description: "The work is open on its provider.",
	},
	CLOSED: {
		label: "Closed",
		icon: CircleSlashIcon,
		badgeVariant: "secondary",
		description: "The work was closed on its provider without a merge.",
	},
	MERGED: {
		label: "Merged",
		icon: GitMergeIcon,
		badgeVariant: "secondary",
		description: "The changes were merged into their target branch.",
	},
};

/** The combined state of the checks on the work's head commit, as the provider last reported it. */
export const REVIEWED_WORK_CHECK_DEFS: StatusDefs<ReviewedWorkCheckState> = {
	SUCCESS: {
		label: "Checks passed",
		icon: CircleCheckIcon,
		badgeVariant: "success",
		description: "Every check on the head commit passed.",
	},
	FAILURE: {
		label: "Checks failed",
		icon: CircleXIcon,
		badgeVariant: "destructive",
		description: "At least one check on the head commit failed.",
	},
	PENDING: {
		label: "Checks running",
		icon: ClockIcon,
		badgeVariant: "outline",
		description: "Checks on the head commit have not finished.",
	},
	CANCELLED: {
		label: "Checks canceled",
		icon: BanIcon,
		badgeVariant: "secondary",
		description: "The checks on the head commit were canceled.",
	},
	NONE: {
		label: "No checks reported",
		icon: CircleMinusIcon,
		badgeVariant: "outline",
		description: "The provider reported no check for the head commit.",
	},
	NO_PIPELINE: {
		label: "No pipeline",
		icon: CircleOffIcon,
		badgeVariant: "outline",
		description:
			"The provider reported no pipeline for the head commit. That does not say whether CI is set up.",
	},
	SKIPPED: {
		label: "Pipeline skipped",
		icon: SkipForwardIcon,
		badgeVariant: "outline",
		description: "The pipeline for the head commit was skipped: neither a pass nor a failure.",
	},
};
