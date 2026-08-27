import {
	GitPullRequestIcon,
	IssueOpenedIcon,
	type Icon as OcticonComponent,
} from "@primer/octicons-react";
import type { FeedbackSourceCount, PracticeGroupStanding } from "@/api/types.gen";
import { type BrandIcon, SlackIcon } from "@/components/icons/brand";

export type PracticeGroupStandingKey = PracticeGroupStanding["standing"];

const VERDICT_STANDINGS = ["DEVELOPING", "MIXED", "STRENGTH"] as const;

export function isVerdictStanding(standing: PracticeGroupStandingKey): boolean {
	return (VERDICT_STANDINGS as readonly string[]).includes(standing);
}

export const PRACTICE_GROUP_STANDING_BADGE: Record<
	PracticeGroupStandingKey,
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
		explanation: "Recent reviews raised problems in this group.",
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

export const PRACTICE_GROUP_SOURCE_META: Partial<
	Record<
		FeedbackSourceCount["workKind"],
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
