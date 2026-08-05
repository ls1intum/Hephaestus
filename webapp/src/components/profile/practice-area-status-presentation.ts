import {
	GitPullRequestIcon,
	IssueOpenedIcon,
	type Icon as OcticonComponent,
} from "@primer/octicons-react";
import { type LucideIcon, TrendingDownIcon, TrendingUpIcon } from "lucide-react";
import type { FeedbackSourceCount, PracticeAreaStatus } from "@/api/types.gen";
import { type BrandIcon, SlackIcon } from "@/components/icons/brand";

/** Shared formative labels and badge variants for every practice-area status surface. */
export const PRACTICE_AREA_STATUS_BADGE: Record<
	PracticeAreaStatus["status"],
	{ label: string; variant: "destructive" | "warning" | "success" | "outline"; ringClass?: string }
> = {
	DEVELOPING: {
		label: "Needs attention",
		variant: "destructive",
		ringClass: "ring-destructive/40 dark:ring-destructive/50",
	},
	MIXED: {
		label: "Mixed feedback",
		variant: "warning",
		ringClass: "ring-warning/40 dark:ring-warning/50",
	},
	STRENGTH: {
		label: "Going well",
		variant: "success",
		ringClass: "ring-success/40 dark:ring-success/50",
	},
	NO_DATA: { label: "No feedback yet", variant: "outline" },
};

/**
 * Display metadata per feedback source kind, mirroring the activity monitor's icon language. The map is
 * the single point to extend when a new observable integration lands server-side; a source the webapp
 * does not know yet is skipped rather than rendered broken.
 */
export const PRACTICE_AREA_SOURCE_META: Partial<
	Record<
		FeedbackSourceCount["source"],
		{ Icon: OcticonComponent | BrandIcon; singular: string; plural: string }
	>
> = {
	PULL_REQUEST: { Icon: GitPullRequestIcon, singular: "pull request", plural: "pull requests" },
	ISSUE: { Icon: IssueOpenedIcon, singular: "issue", plural: "issues" },
	CONVERSATION_THREAD: {
		Icon: SlackIcon,
		singular: "Slack conversation",
		plural: "Slack conversations",
	},
};

/**
 * Only directional changes need a hint; STEADY is intentionally left quiet. The explanation mirrors
 * the server's derivation (day-to-day comparison of averaged feedback across the area's practices)
 * so the hover answers "where does this arrow come from?" truthfully.
 */
export const PRACTICE_AREA_TREND_HINT: Partial<
	Record<
		NonNullable<PracticeAreaStatus["trajectory"]>,
		{ label: string; Icon: LucideIcon; explanation: string }
	>
> = {
	IMPROVING: {
		label: "Improving lately",
		Icon: TrendingUpIcon,
		explanation:
			"Compares the two most recent days with feedback across this area's practices. The latest day shows more strengths or fewer areas to work on.",
	},
	REGRESSING: {
		label: "More to work on lately",
		Icon: TrendingDownIcon,
		explanation:
			"Compares the two most recent days with feedback across this area's practices. The latest day shows more areas to work on or fewer strengths.",
	},
};
