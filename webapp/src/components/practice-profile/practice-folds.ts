// The words and the arithmetic behind the folds of the Practice profile: practices and groups that
// never applied to the developer's work, and the ones no review has observed yet. Each reads only
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
 * The fold of practices or groups that no review has observed in the reader's work yet. Its words
 * are the standing's: the badge says "Not observed yet", a count "Not observed", and the fold and
 * its sentence say "not observed yet" and "no review has observed … in the last 90 days", so one
 * phrase names the one state.
 */
export const NOT_OBSERVED_YET = {
	practiceLabel: "Practices not observed yet",
	groupLabel: "Practice groups not observed yet",
	practiceDescription: `No review has observed these practices in your work ${WINDOW}. Each gets a standing once a review does.`,
	groupDescription: `No review has observed a practice of these groups in your work ${WINDOW}, or the practice did not apply to it. Open a group to see why.`,
} as const;

export const isNeverApplied = (practice: PracticeStanding): boolean =>
	practice.standing === "NO_OPPORTUNITY" && practice.doesNotApplyOnly;

/** No review has observed the practice in the reader's work yet. */
export const isNotObservedYet = (practice: PracticeStanding): boolean =>
	practice.standing === "NOT_OBSERVED";

/**
 * The practices split three ways: the ones the table lists, the ones that never applied, and the
 * ones no review has observed yet. Each of the last two folds into one line under the table.
 */
export function splitFoldedPractices<T extends PracticeStanding>(
	practices: readonly T[],
): { shown: T[]; neverApplied: T[]; notObserved: T[] } {
	return {
		shown: practices.filter((practice) => !isNeverApplied(practice) && !isNotObservedYet(practice)),
		neverApplied: practices.filter(isNeverApplied),
		notObserved: practices.filter(isNotObservedYet),
	};
}

/** A group in which every practice never applied. */
export const groupNeverApplied = (practices: readonly PracticeStanding[]): boolean =>
	practices.length > 0 && practices.every(isNeverApplied);

/**
 * A group in which no review has observed any practice in the reader's work yet. A practice that
 * never applied counts the same, so a group that mixes the two folds here too, as its own level
 * then shows nothing but folds. A group whose every practice never applied has its own fold.
 */
export const groupNotObservedYet = (practices: readonly PracticeStanding[]): boolean =>
	practices.some(isNotObservedYet) &&
	practices.every((practice) => isNotObservedYet(practice) || isNeverApplied(practice));
