import type { ReviewResultCounts } from "@/api/types.gen";
import type { BadgeVariant } from "@/components/common/status-def";
import type { WithholdingReason } from "@/components/practice-vocabulary/withholding-defs";

import { PRACTICE_MARKER_THRESHOLDS } from "./review-attention-thresholds";

/**
 * The tones an attention marker on the overview can take: neutral is information, red says
 * something is broken now that an admin can fix (`review-attention-thresholds.ts`). There is no
 * amber. They are the status tones every badge uses.
 */
export type AttentionTone = Extract<BadgeVariant, "secondary" | "destructive">;

/** The share `count` is of `whole`, from 0 to 1; nothing of nothing is nothing. */
export function shareOf(count: number, whole: number): number {
	return whole > 0 ? count / whole : 0;
}

/** "5 %", "0.4 %": whole from one up, one decimal below, so a small loss never reads as none. */
export function percentText(share: number): string {
	const percent = share * 100;
	if (percent === 0) {
		return "0 %";
	}
	return percent >= 1
		? `${Math.round(percent)} %`
		: `${Math.max(0.1, Math.round(percent * 10) / 10)} %`;
}

/**
 * Why withheld feedback did not reach the developer, by what it asks of an admin. Only timing is an
 * operating problem: the work moved on before the comment was ready. The review holding a piece back
 * is the review working as meant, and the developer's choice is theirs; anything else, such as an
 * admin's own correction or a setting, is listed as other.
 */
export type WithheldClass = "TIMING" | "HELD_BY_REVIEW" | "DEVELOPER_CHOICE" | "OTHER";

const REASON_CLASS: Record<WithholdingReason, WithheldClass> = {
	ITEM_MERGED: "TIMING",
	ITEM_CLOSED: "TIMING",
	STALE_ROLLOUT_REVISION: "TIMING",
	REVIEWED_VERSION_CHANGED: "TIMING",
	COMPOSER_WITHHELD: "HELD_BY_REVIEW",
	NOTHING_NEW: "HELD_BY_REVIEW",
	REPEATS_DELIVERED_NOTE: "HELD_BY_REVIEW",
	VOLUME_CAPPED: "HELD_BY_REVIEW",
	BACKFILL_QUIET: "HELD_BY_REVIEW",
	NO_AI: "DEVELOPER_CHOICE",
	DELIVERY_OFF: "DEVELOPER_CHOICE",
	REACTED_DISAGREE: "DEVELOPER_CHOICE",
	REACTED_NOT_APPLICABLE: "DEVELOPER_CHOICE",
	PERSON_SUPPRESSED: "OTHER",
	RECIPIENT_UNAVAILABLE: "OTHER",
	REVIEW_CANCELLED: "OTHER",
	ITEM_GONE: "OTHER",
	ITEM_STATE_INELIGIBLE: "OTHER",
	ISSUE_SNAPSHOT_CHANGED: "OTHER",
	CONVERSATION_EXPIRED: "OTHER",
	DELIVERY_UNCONFIRMED: "OTHER",
	EMPTY_AFTER_SANITIZE: "OTHER",
	PUBLIC_SUBJECT_INELIGIBLE: "OTHER",
	INSTANCE_SILENCED: "OTHER",
	FEEDBACK_OFF: "OTHER",
	WORKSPACE_DISABLED: "OTHER",
	WORKSPACE_DELIVERY_PAUSED: "OTHER",
	OUTSIDE_CURRENT_SCOPE: "OTHER",
	APPROVAL_STALE: "OTHER",
	APPROVAL_NO_LONGER_ELIGIBLE: "OTHER",
	PRACTICE_REQUIRES_APPROVAL: "OTHER",
	RESULT_MARKED_INCORRECT: "OTHER",
};

export function withheldClass(reason: WithholdingReason): WithheldClass {
	return REASON_CLASS[reason];
}

/**
 * What a practice's results suggest an admin look at: a practice that mostly does not apply here
 * costs reviews and tells nobody much, and one that often cannot decide may lack the evidence it needs.
 */
export type PracticeMarker = "NEVER_APPLIES" | "OFTEN_UNDECIDED";

export interface PracticeMarkerDef {
	label: string;
	/** The marker's reason with its numbers: "Does not apply in 179 of 179 results. …" */
	reason: (count: number, whole: number) => string;
}

export const PRACTICE_MARKER_DEFS: Record<PracticeMarker, PracticeMarkerDef> = {
	NEVER_APPLIES: {
		label: "Mostly not applicable",
		reason: (count, whole) =>
			`Does not apply in ${count} of ${whole} results. Consider turning it off for this workspace.`,
	},
	OFTEN_UNDECIDED: {
		label: "Often undecided",
		reason: (count, whole) =>
			`Could not determine in ${count} of ${whole} results. The review may lack the evidence to decide.`,
	},
};

/** The one marker a practice's results earn, the stronger first; none below the minimum. */
export function practiceMarker(
	counts: ReviewResultCounts,
): { marker: PracticeMarker; count: number; whole: number } | undefined {
	const whole = counts.met + counts.notMet + counts.doesNotApply + counts.couldNotDetermine;
	if (whole < PRACTICE_MARKER_THRESHOLDS.minimumResults) {
		return undefined;
	}
	if (shareOf(counts.doesNotApply, whole) >= PRACTICE_MARKER_THRESHOLDS.neverAppliesShare) {
		return { marker: "NEVER_APPLIES", count: counts.doesNotApply, whole };
	}
	if (shareOf(counts.couldNotDetermine, whole) >= PRACTICE_MARKER_THRESHOLDS.oftenUndecidedShare) {
		return { marker: "OFTEN_UNDECIDED", count: counts.couldNotDetermine, whole };
	}
	return undefined;
}

const MARKER_RANK: Record<PracticeMarker, number> = { NEVER_APPLIES: 0, OFTEN_UNDECIDED: 1 };
const UNMARKED_RANK = 2;

/**
 * The practices in the order "Needs a look first": the ones with a marker ahead, the stronger marker
 * first; then the most results an admin marked incorrect, then the most that could not be determined, since
 * both say the review got the practice wrong or could not judge it. Ties keep the order they came
 * in, which is busiest first, so a range with nothing to look at reads as busiest first.
 */
export function needsALookFirst<
	T extends { results: ReviewResultCounts; resultsMarkedIncorrect: number },
>(rows: readonly T[]): T[] {
	const rank = (row: T) => {
		const found = practiceMarker(row.results);
		return found === undefined ? UNMARKED_RANK : MARKER_RANK[found.marker];
	};
	return rows
		.map((row, index) => ({ row, index, rank: rank(row) }))
		.sort(
			(a, b) =>
				a.rank - b.rank ||
				b.row.resultsMarkedIncorrect - a.row.resultsMarkedIncorrect ||
				b.row.results.couldNotDetermine - a.row.results.couldNotDetermine ||
				a.index - b.index,
		)
		.map(({ row }) => row);
}
