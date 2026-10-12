// The pattern of a practice, read from the open feedback card the developer's own page already
// holds. Nothing here names another developer.

import { isOpenFeedback } from "@/components/practice-vocabulary/feedback-state-defs";
import type { PracticeFeedbackCardEntry } from "@/components/practice-vocabulary/PracticeFeedbackCard";

import { newestFirst } from "./practice-feedback-cards";

export interface PracticePattern {
	/** The clean work since the card was prepared, as the card's own meter counts it. */
	cleanCount: number;
	cleanNeeded: number;
}

/**
 * The pattern of a practice, read from its open card: how far the clean run since the card has
 * come. Every open card counts, also one that rests on a single piece of work, so the label in the
 * group's table agrees with "Current feedback" on the practice's own level.
 */
export function practicePatternOf(
	cards: readonly PracticeFeedbackCardEntry[],
	practiceSlug: string,
): PracticePattern | undefined {
	const card = newestFirst(
		cards.filter((each) => each.practiceSlug === practiceSlug && isOpenFeedback(each.state)),
	)[0];
	if (card === undefined) {
		return undefined;
	}
	return {
		cleanCount: card.cleanWork.length,
		cleanNeeded: card.cleanNeeded,
	};
}
