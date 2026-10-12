import {
	ArchiveIcon,
	CirclePlusIcon,
	FileTextIcon,
	FlagIcon,
	GitCommitHorizontalIcon,
	GitMergeIcon,
	MessageSquareIcon,
	MessagesSquareIcon,
	PencilIcon,
	RefreshCwIcon,
} from "lucide-react";

import type { ReviewLineage } from "@/api/types.gen";
import type { StatusDefs } from "@/components/common/status-def";

import { OBSERVATION_ORIGIN_DEFS, REVIEW_OCCASION_LABELS } from "./observation-origin-defs";

export type ReviewTrigger = NonNullable<ReviewLineage["reviewTrigger"]>;

/**
 * What started a review: the first moment on the work that names it. A review asked for by hand, a
 * backfill and a scheduled check are named as that, whatever the work did. None of these is good or
 * bad on its own, so all are neutral.
 */
export const REVIEW_TRIGGER_DEFS: StatusDefs<ReviewTrigger> = {
	OPENED: {
		label: "Work opened",
		icon: CirclePlusIcon,
		badgeVariant: "outline",
		description: "The review started when the work was opened.",
	},
	READY: {
		label: "Marked ready for review",
		icon: FlagIcon,
		badgeVariant: "outline",
		description: "The review started when the work left draft.",
	},
	PUSHED: {
		label: "New commits",
		icon: GitCommitHorizontalIcon,
		badgeVariant: "outline",
		description: "The review started when new commits reached the work.",
	},
	EDITED: {
		label: "Work edited",
		icon: PencilIcon,
		badgeVariant: "outline",
		description: "The review started when the work’s title, description or linked issue changed.",
	},
	REVIEWED: {
		label: "A person reviewed the work",
		icon: MessageSquareIcon,
		badgeVariant: "outline",
		description: "The review started when someone submitted a review on the work.",
	},
	MERGED: {
		label: "Work merged",
		icon: GitMergeIcon,
		badgeVariant: "outline",
		description:
			"The review started when the work was merged. Practices that look at a merge run then by design.",
	},
	CLOSED: {
		label: "Work closed",
		icon: ArchiveIcon,
		badgeVariant: "outline",
		description: "The review started when the work was closed.",
	},
	PUBLISHED: {
		label: "Document published",
		icon: FileTextIcon,
		badgeVariant: "outline",
		description: "The review started when the document was published.",
	},
	SETTLED: {
		label: "Conversation settled",
		icon: MessagesSquareIcon,
		badgeVariant: "outline",
		description: "The review started when the conversation went quiet.",
	},
	// The origin's own words, so a review and its observations say how it came about alike.
	REQUESTED: OBSERVATION_ORIGIN_DEFS.MANUAL,
	BACKFILL: OBSERVATION_ORIGIN_DEFS.BACKFILL,
	SWEEP: {
		label: REVIEW_OCCASION_LABELS.SWEEP,
		icon: RefreshCwIcon,
		badgeVariant: "outline",
		description: "A scheduled check looked at recent work again.",
	},
};
