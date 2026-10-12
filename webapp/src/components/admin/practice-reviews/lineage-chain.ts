import type {
	ReviewLineage,
	ReviewLineageFeedback,
	ReviewLineageResult,
	ReviewedWorkRef,
} from "@/api/types.gen";
import type { BadgeVariant, StatusDef } from "@/components/common/status-def";
import { deliveryOutcome } from "@/components/practice-vocabulary/delivery-outcome-defs";
import {
	FEEDBACK_LOCATION_DEFS,
	type FeedbackLocation,
} from "@/components/practice-vocabulary/feedback-location-defs";
import { FEEDBACK_WITHDRAWN_DEF } from "@/components/practice-vocabulary/feedback-withdrawal-defs";
import { OUTCOME_DEFS, type Outcome } from "@/components/practice-vocabulary/outcome-defs";
import {
	type WithholdingReason,
	withholdingReasonSentence,
} from "@/components/practice-vocabulary/withholding-defs";
import { durationBetween } from "@/lib/dates";

import { QUEUE_WAIT_THRESHOLDS } from "./review-attention-thresholds";

/**
 * A gap this long between two steps is a delay worth seeing, so it is coloured: the overview's amber
 * mark for the queue, so a trace and the overview call the same wait slow.
 */
export const SLOW_GAP_SECONDS = QUEUE_WAIT_THRESHOLDS.warningSeconds;

/**
 * Whether a gap is a long wait, judged on the minutes `gapLabel` shows, so a gap shown as "30 min"
 * is always coloured as one.
 */
export function isSlowGap(seconds: number): boolean {
	return seconds >= 60 && Math.round(seconds / 60) * 60 >= SLOW_GAP_SECONDS;
}

/** A dot in the tone of a status, where an icon would be too large. */
const TONE_BG: Record<BadgeVariant, string> = {
	default: "bg-primary",
	secondary: "bg-muted-foreground/60",
	destructive: "bg-destructive",
	outline: "bg-muted-foreground/60",
	success: "bg-success",
	warning: "bg-warning",
};

export function toneBg(variant: BadgeVariant): string {
	return TONE_BG[variant];
}

const TONE_BORDER: Record<BadgeVariant, string> = {
	default: "border-primary",
	secondary: "border-border",
	destructive: "border-destructive",
	outline: "border-border",
	success: "border-success",
	warning: "border-warning",
};

export function toneBorder(variant: BadgeVariant): string {
	return TONE_BORDER[variant];
}

/** A tinted chip in the tone of a status: readable in both themes, where a filled dot would not be. */
const TONE_CHIP: Record<BadgeVariant, string> = {
	default: "border-primary/50 bg-primary/10 text-primary",
	secondary: "border-muted-foreground/40 bg-muted text-muted-foreground",
	destructive: "border-destructive/50 bg-destructive/10 text-destructive",
	outline: "border-muted-foreground/40 bg-muted text-muted-foreground",
	success: "border-success/50 bg-success/10 text-success",
	warning: "border-warning/50 bg-warning/10 text-warning",
};

export function toneChip(variant: BadgeVariant): string {
	return TONE_CHIP[variant];
}

/**
 * Where one piece of feedback appears. The lineage names the place as the feedback list does; a
 * record without one falls back to the channel, with the provider read from the work.
 */
export function feedbackLocation(
	feedback: Pick<ReviewLineageFeedback, "channel"> & { location?: FeedbackLocation },
	work: ReviewedWorkRef | undefined,
): FeedbackLocation {
	if (feedback.location !== undefined) {
		return feedback.location;
	}
	switch (feedback.channel) {
		case "IN_CONTEXT": {
			if (work?.provider === "GITHUB") {
				return "GITHUB";
			}
			if (work?.provider === "GITLAB") {
				return "GITLAB";
			}
			return "OTHER_WORK";
		}
		case "IN_CHAT": {
			return "NEXT_CONVERSATION";
		}
		case "IN_APP": {
			return "PRACTICE_PAGES";
		}
	}
}

/** "On GitHub" reads "on GitHub" after a verb; a provider name keeps its case. */
export function lowerFirst(value: string): string {
	return value.charAt(0).toLowerCase() + value.slice(1);
}

/**
 * A few words for every withholding reason, short enough for one line of the story. The full
 * sentence stays with `withholdingReasonSentence`.
 */
const SHORT_REASON: Record<WithholdingReason, string> = {
	ARTIFACT_MERGED: "already merged",
	REVIEW_CANCELLED: "review cancelled",
	ARTIFACT_CLOSED: "work closed",
	ARTIFACT_GONE: "work gone",
	ARTIFACT_DRAFT: "still a draft",
	COMPOSER_DEDUPED: "near duplicate",
	COMPOSER_WITHHELD: "held back by the review",
	REPEATS_DELIVERED_NOTE: "repeats a posted note",
	OBSERVATION_INVALIDATED: "result marked incorrect",
	VOLUME_CAPPED: "over the volume limit",
	RECIPIENT_OPTED_OUT: "developer opted out",
	REACTED_DISPUTED: "disputed before",
	REACTED_NOT_APPLICABLE: "marked not applicable before",
	CONVERSATION_EXPIRED: "no conversation came",
	BACKFILL_QUIET: "past work, never sent",
	INSTANCE_SILENCED: "silent mode",
	WORKSPACE_DELIVERY_PAUSED: "delivery paused",
	ISSUE_SNAPSHOT_CHANGED: "issue changed",
	EMPTY_AFTER_SANITIZE: "nothing left to send",
	WORKSPACE_DISABLED: "feedback off here",
	STALE_ROLLOUT_REVISION: "settings changed",
	OUTSIDE_CURRENT_COVERAGE: "no longer covered",
	APPROVAL_STALE: "approval out of date",
	APPROVAL_NO_LONGER_ELIGIBLE: "approval no longer needed",
	PRACTICE_REQUIRES_APPROVAL: "needs approval",
	REVIEWED_REVISION_CHANGED: "change updated since",
	PUBLIC_SUBJECT_INELIGIBLE: "not about the author’s work",
};

export function shortReason(reason: WithholdingReason): string {
	return SHORT_REASON[reason];
}

export interface BranchOutcome {
	/** Icon and tone. */
	def: StatusDef;
	/** "Delivered on GitHub", "Withheld: already merged". */
	text: string;
	/** The full reason, where the short text cuts one. */
	detail?: string;
	/** When it happened, where the record says. */
	at?: Date;
	/**
	 * `delivered` reached the developer and stands; `withheld` never reached them, or no longer
	 * stands; `waiting` is still on its way or waits for a person; `replaced` a newer piece took its
	 * place.
	 */
	kind: "delivered" | "withheld" | "waiting" | "replaced" | "other";
}

/**
 * What became of one piece of feedback, in one line: where it landed, or why it did not. An
 * admin's withdrawal or rejection wins over the stored state, since that is the last thing that
 * happened to it; a piece shown and then withheld says both.
 */
export function branchOutcome(
	feedback: ReviewLineageFeedback,
	work: ReviewedWorkRef | undefined,
): BranchOutcome {
	const def = deliveryOutcome(feedback);
	const location = FEEDBACK_LOCATION_DEFS[feedbackLocation(feedback, work)];
	if (feedback.withdrawnAt !== undefined) {
		return {
			def: FEEDBACK_WITHDRAWN_DEF,
			text: "Withdrawn by an admin",
			// A prepared piece can be withdrawn too: only a recorded delivery says it was delivered.
			detail: reachedDeveloper(feedback)
				? `Delivered ${lowerFirst(location.label)}, then taken off them.`
				: "Taken back before it reached the developer.",
			at: feedback.withdrawnAt,
			kind: "withheld",
		};
	}
	if (feedback.deliveryState === "DISCARDED") {
		return {
			def,
			text: "Rejected by an admin",
			detail: "An admin decided it should not be sent.",
			at: feedback.decidedAt,
			kind: "withheld",
		};
	}
	if (feedback.deliveryState === "AWAITING_APPROVAL") {
		return { def, text: "Awaiting approval", kind: "waiting" };
	}
	if (feedback.deliveryState === "DELIVERED") {
		return {
			def,
			text: `Delivered ${lowerFirst(location.label)}`,
			at: feedback.deliveredAt,
			kind: "delivered",
		};
	}
	if (feedback.deliveryState === "SUPERSEDED") {
		return { def, text: "Replaced by newer", at: feedback.replacedAt, kind: "replaced" };
	}
	if (feedback.suppressionReason !== undefined) {
		const shownFirst = feedback.deliveredAt !== undefined;
		let prefix = def.label;
		if (feedback.deliveryState === "SUPPRESSED") {
			prefix = shownFirst ? "Delivered, later withheld" : "Withheld";
		}
		return {
			def,
			text: `${prefix}: ${shortReason(feedback.suppressionReason)}`,
			detail: withholdingReasonSentence(feedback.suppressionReason),
			at: feedback.deliveredAt,
			kind: "withheld",
		};
	}
	if (feedback.deliveryState === "PREPARED" || feedback.deliveryState === "PARTIALLY_DELIVERED") {
		return { def, text: def.label, at: feedback.deliveredAt, kind: "waiting" };
	}
	return { def, text: def.label, at: feedback.deliveredAt, kind: "other" };
}

/** Whether the developer could read it at some point, though it may not stand now. */
export function reachedDeveloper(feedback: ReviewLineageFeedback): boolean {
	return feedback.deliveryState === "DELIVERED" || feedback.deliveredAt !== undefined;
}

/** "+40 s", "+2 min", "+1 h 17 min", "+2 d 3 h": {@link durationText} with a sign, read between two steps. */
export function gapLabel(from: Date, to: Date): string {
	return `${to.getTime() < from.getTime() ? "−" : "+"}${durationBetween(from, to)}`;
}

const OUTCOME_ORDER: readonly Outcome[] = ["NOT_MET", "MET", "UNDETERMINED", "NOT_APPLICABLE"];

/** The results by verdict, worst first, with the zeroes left out. */
export function verdictCounts(
	results: readonly ReviewLineageResult[],
): { outcome: Outcome; count: number }[] {
	return OUTCOME_ORDER.map((outcome) => ({
		outcome,
		count: results.filter((result) => result.outcome === outcome).length,
	})).filter((entry) => entry.count > 0);
}

/** "5 results: 3 not met, 2 met". The ones the list leaves out are counted, not judged. */
export function verdictSummary(lineage: Pick<ReviewLineage, "results" | "moreResults">) {
	const total = lineage.results.length + lineage.moreResults;
	const parts = verdictCounts(lineage.results).map(
		({ outcome, count }) => `${count} ${OUTCOME_DEFS[outcome].label.toLowerCase()}`,
	);
	if (lineage.moreResults > 0) {
		parts.push(`${lineage.moreResults} not listed`);
	}
	return `${countOf(total, "result", "results")}: ${parts.join(", ")}`;
}

export function countOf(count: number, singular: string, plural: string): string {
	return `${count} ${count === 1 ? singular : plural}`;
}

export interface NumberedResult {
	result: ReviewLineageResult;
	number: number;
}

/** The numbered results by id, as `numberedResults` numbers them. */
export type NumberedById = ReadonlyMap<string, NumberedResult>;

/**
 * The results in the order the chain numbers them: by when they were observed, then by id,
 * grouped by practice in the order a practice was first observed. The number is the position, from
 * 1. The order rests on the review alone, never on the record a trace was opened from or the order
 * the lineage lists them in, so a result keeps its number in every trace of its review.
 */
export function numberedResults(results: readonly ReviewLineageResult[]): NumberedResult[] {
	const byTime = [...results].sort(
		(a, b) => a.observedAt.getTime() - b.observedAt.getTime() || a.id.localeCompare(b.id, "en"),
	);
	const groups = new Map<string, ReviewLineageResult[]>();
	for (const result of byTime) {
		const group = groups.get(result.practiceSlug) ?? [];
		group.push(result);
		groups.set(result.practiceSlug, group);
	}
	return [...groups.values()].flat().map((result, index) => ({ result, number: index + 1 }));
}

/**
 * Not-met results of the review that led to no feedback of its own. A met, not applicable or
 * undetermined one rarely leads to feedback, so it is no loss; feedback a later review wrote from
 * one does not count for this review.
 */
export function silentNotMet(lineage: ReviewLineage): ReviewLineageResult[] {
	const reviewId = lineage.review?.id;
	return lineage.results.filter(
		(result) =>
			result.outcome === "NOT_MET" &&
			(reviewId === undefined || result.agentJobId === reviewId) &&
			result.ownFeedbackCount === 0,
	);
}

/** How far the chain got, step by step: observed, written, reached the developer, answered. */
export interface StageCounts {
	results: number;
	feedback: number;
	reached: number;
	answered: number;
	resolvedByWork: number;
}

export function stageCounts(lineage: ReviewLineage): StageCounts {
	// Only listed records belong to a known review; omitted chain records may belong to another.
	const reviewId = lineage.review?.id;
	const own = lineage.feedback.filter(
		(entry) => reviewId === undefined || entry.agentJobId === reviewId,
	);
	const reached = own.filter(reachedDeveloper);
	return {
		results: lineage.results.filter(
			(entry) => reviewId === undefined || entry.agentJobId === reviewId,
		).length,
		feedback: own.length,
		reached: reached.length,
		answered: reached.filter((entry) => entry.response?.respondedAt !== undefined).length,
		resolvedByWork: reached.filter((entry) => entry.response?.resolvedByWorkAt !== undefined)
			.length,
	};
}
