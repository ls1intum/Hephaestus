// The words and the arithmetic behind the folds of the Practice profile: practices and groups that
// never applied to the developer's work, and the ones no review has reviewed yet. Each reads only
// what the developer's own page already holds. Nothing here names another developer.

import { CircleDashedIcon } from "lucide-react";

import type { PracticeStanding } from "@/api/types.gen";
import type { StatusDef } from "@/components/common/status-def";
import { STANDING_LOOKBACK_DAYS } from "@/components/practice-vocabulary/practice-trend-presentation";

// The server reads only this window, so every sentence names it: older evidence does not count.
const WINDOW = `in the last ${STANDING_LOOKBACK_DAYS} days`;

export const NEVER_APPLIED = {
	label: "Does not apply to your work yet",
	description: `Every review ${WINDOW} found that this practice did not apply to your work. This is not a gap: it gets a standing once a piece of work calls for it.`,
	groupDescription: `Every review ${WINDOW} found that the practices in these groups did not apply to your work. This is not a gap.`,
	/** The sentence behind one folded group's badge. */
	oneGroupDescription: `Every review ${WINDOW} found that the practices of this group did not apply to your work. This is not a gap.`,
} as const;

/** The standing a practice shows when it never applied, in place of "Nothing to report yet". */
export const NEVER_APPLIED_DEF: StatusDef = {
	label: NEVER_APPLIED.label,
	icon: CircleDashedIcon,
	badgeVariant: "outline",
	description: NEVER_APPLIED.description,
};

/**
 * The fold of practices or groups that no review has reviewed in the reader's work yet. Its words
 * are the standing's: the badge and a count say "Not reviewed", and the fold and its sentence say
 * "not reviewed" and "no review … in the last 90 days", so one phrase names the one state.
 */
export const NOT_REVIEWED_YET = {
	practiceLabel: "Practices not reviewed",
	groupLabel: "Practice groups not reviewed",
	practiceDescription: `No review ${WINDOW} has looked at these practices in your work. Each gets a standing once a review does.`,
	groupDescription: `No review ${WINDOW} has looked at a practice of these groups in your work, or the practice did not apply to it. Open a group to see why.`,
} as const;

export const isNeverApplied = (practice: PracticeStanding): boolean =>
	practice.standing === "NO_OPPORTUNITY" && practice.doesNotApplyOnly;

/** No review has reviewed the practice in the reader's work yet. */
export const isNotReviewedYet = (practice: PracticeStanding): boolean =>
	practice.standing === "NOT_OBSERVED";

/**
 * The practices split three ways: the ones the table lists, the ones that never applied, and the
 * ones no review has reviewed yet. Each of the last two folds into one line under the table.
 */
export function splitFoldedPractices<T extends PracticeStanding>(
	practices: readonly T[],
): { shown: T[]; neverApplied: T[]; notReviewed: T[] } {
	return {
		shown: practices.filter((practice) => !isNeverApplied(practice) && !isNotReviewedYet(practice)),
		neverApplied: practices.filter(isNeverApplied),
		notReviewed: practices.filter(isNotReviewedYet),
	};
}

/** A group in which every practice never applied. */
export const groupNeverApplied = (practices: readonly PracticeStanding[]): boolean =>
	practices.length > 0 && practices.every(isNeverApplied);

/**
 * A group in which no review has reviewed any practice in the reader's work yet. A practice that
 * never applied counts the same, so a group that mixes the two folds here too, as its own level
 * then shows nothing but folds. A group whose every practice never applied has its own fold.
 */
export const groupNotReviewedYet = (practices: readonly PracticeStanding[]): boolean =>
	practices.some(isNotReviewedYet) &&
	practices.every((practice) => isNotReviewedYet(practice) || isNeverApplied(practice));
