import {
	GitPullRequestIcon,
	IssueOpenedIcon,
	type Icon as OcticonComponent,
} from "@primer/octicons-react";
import type { FeedbackSourceCount, PracticeAreaStatus } from "@/api/types.gen";
import { type BrandIcon, SlackIcon } from "@/components/icons/brand";

export type PracticeAreaStatusKey = PracticeAreaStatus["status"];

/** The three statuses that carry a verdict about the developer's work. */
const VERDICT_STATUSES = ["DEVELOPING", "MIXED", "STRENGTH"] as const;

/**
 * Whether a status is a verdict rather than a reason no verdict could be formed. Mirrors the server's
 * `PracticeAreaStatusDTO.isVerdict`; the surfaces branch on this instead of testing single enum values, so
 * adding a further no-verdict reason does not need a hunt through the components.
 */
export function isVerdictStatus(status: PracticeAreaStatusKey): boolean {
	return (VERDICT_STATUSES as readonly string[]).includes(status);
}

/**
 * Shared formative labels and badge variants for every practice-area status surface.
 *
 * <p>The three no-verdict statuses are deliberately worded differently from each other. The earlier single
 * `NO_DATA`/"No feedback yet" state made "nothing was reviewed", "your work offered no opportunity", and
 * "problems were seen but withheld as too uncertain" indistinguishable — so a learner could not tell a
 * working detector from an unconfigured one, and the label claimed something about feedback while the
 * trigger was observations. `explanation` is the sentence a surface shows when it has no standing ring to
 * explain the state for it.
 */
export const PRACTICE_AREA_STATUS_BADGE: Record<
	PracticeAreaStatusKey,
	{
		label: string;
		variant: "destructive" | "warning" | "success" | "outline";
		ringClass?: string;
		explanation: string;
	}
> = {
	DEVELOPING: {
		label: "Needs attention",
		variant: "destructive",
		ringClass: "ring-destructive/40 dark:ring-destructive/50",
		explanation: "Recent reviews raised problems in this area.",
	},
	MIXED: {
		label: "Mixed feedback",
		variant: "warning",
		ringClass: "ring-warning/40 dark:ring-warning/50",
		explanation: "Recent reviews found both strengths and problems here.",
	},
	STRENGTH: {
		label: "Going well",
		variant: "success",
		ringClass: "ring-success/40 dark:ring-success/50",
		explanation: "Nothing needs your attention here right now.",
	},
	NOT_OBSERVED: {
		label: "Not observed yet",
		variant: "outline",
		explanation: "These practices have not been observed in your reviewed work yet.",
	},
	NO_OPPORTUNITY: {
		label: "Nothing to report yet",
		variant: "outline",
		explanation:
			"Your recent work was reviewed, but it either offered no opportunity for these practices or raised nothing worth mentioning.",
	},
};

/**
 * Display metadata per feedback source kind, mirroring the activity monitor's icon language. The map is
 * the single point to extend when a new observable integration lands server-side; a source the webapp
 * does not know yet is skipped rather than rendered broken.
 */
export const PRACTICE_AREA_SOURCE_META: Partial<
	Record<
		FeedbackSourceCount["artifactKind"],
		{ Icon: OcticonComponent | BrandIcon; singular: string; plural: string }
	>
> = {
	"scm.pull_request": {
		Icon: GitPullRequestIcon,
		singular: "pull request",
		plural: "pull requests",
	},
	"scm.issue": { Icon: IssueOpenedIcon, singular: "issue", plural: "issues" },
	"chat.conversation_thread": {
		Icon: SlackIcon,
		singular: "Slack conversation",
		plural: "Slack conversations",
	},
};
