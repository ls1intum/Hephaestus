import { count } from "@/components/practice-vocabulary/feedback-text";

import type { InfoLine } from "@/components/common/InfoTip";

/** The page's short name: its sidebar entry and the first crumb of its levels. */
export const ACROSS_THE_WORKSPACE = "Across the workspace";

/** One action from each practice row, so one label. */
export const OPEN_IN_YOUR_PROFILE = "Open in your Practice profile";

/** Delay before the page explains a slow read. */
export const SLOW_LOAD_AFTER_MS = 4000;

/**
 * Delay before the loading line shows and is announced: a read that is in by then only flashes
 * the skeletons, and the live region is already in the page when its message arrives.
 */
export const LOADING_LINE_AFTER_MS = 200;

/** The loading line above the skeletons, and the page's one announcement while it counts. */
export const LOADING_STATUS = "Counting results across the workspace…";

/** The page's one visible line under its title; {@link PAGE_INTRO} says the rest behind an (i). */
export const PAGE_INTRO_SHORT = "How each practice goes across this workspace. Not a grade.";

/** Behind the (i) beside {@link PAGE_INTRO_SHORT}. */
export const PAGE_INTRO: readonly InfoLine[] = [
	{
		term: "What it shows:",
		text: "for each practice, how many developers have results and how those results came out.",
	},
	{
		term: "No names:",
		text: "it counts developers and results, never who they are.",
	},
	{
		term: "Not a grade:",
		text: "each result comes from AI review of someone's work and can be wrong.",
	},
	{ term: "Your own results:", text: "they are in your Practice profile." },
];

/**
 * The loading line once the counts take longer than {@link SLOW_LOAD_AFTER_MS}; {@link SLOW_LOAD_NOTE}
 * says the rest behind an (i). Short enough to stay on one line at 390px.
 */
export const SLOW_LOAD_SHORT = "Counting takes longer than usual…";

/** Behind the (i) of the loading line. */
export const SLOW_LOAD_NOTE: readonly InfoLine[] = [
	{
		term: "A large course:",
		text: "counting every result in the workspace takes a moment.",
	},
	{ term: "Then:", text: "the page fills in when the counts are ready." },
];

/** Keep the counts fresh across brief focus changes. */
export const ACROSS_WORKSPACE_STALE_MS = 5 * 60 * 1000;

/** The overview failed: the page and the practice groups' levels have nothing to show. */
export const GROUPS_LOAD_ERROR = "We could not load the practice groups";

/** Digits always: a count of developers sits beside other figures, never in running prose. */
export const developersText = (n: number): string => count(n, "developer", "developers", true);

/** Under the page's groups: who the counts are about. */
export const developersInReview = (n: number): string =>
	`Results for the ${developersText(n)} whose work this workspace reviews.`;

/** Digits always, as {@link developersText}. */
export const practicesText = (n: number): string => count(n, "practice", "practices", true);

/** A practice or group with every outcome count at zero. */
export const NO_RESULTS_YET = "No results yet";
