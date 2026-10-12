import type { ReviewTab } from "./review-scope";

/**
 * The one question each tab answers, in the reader's words. A tab that answers a different question
 * belongs on another tab.
 */
const PURPOSES: Record<ReviewTab, string> = {
	overview: "See whether anything needs you and whether reviews are running.",
	runs: "See what happened in each review. Open a review to follow it from start to end.",
	unreviewed: "Find out why no review ran on a piece of work.",
	observations: "Check whether the reviews judged each practice correctly.",
	feedback: "See what feedback went out, what did not, and why.",
};

export interface ReviewTabIntroProps {
	tab: ReviewTab;
}

/** One short line under the tabs that says what the tab in front is for. */
export function ReviewTabIntro({ tab }: ReviewTabIntroProps) {
	return <p className="text-sm text-muted-foreground">{PURPOSES[tab]}</p>;
}
