import { CircleDotIcon, FileTextIcon, GitPullRequestIcon, MessagesSquareIcon } from "lucide-react";
import type { PracticeTraceEntry, TracedSignal } from "@/api/types.gen";
import { SUPPRESSION_REASON_LABELS } from "@/components/admin/practice-reviews/review-format";
import { ARTIFACT_KIND } from "@/lib/artifact-kinds";

/**
 * Every label the trace surface prints is keyed off the generated wire union rather than a hand-kept
 * string list, so a value the server adds or renames fails `typecheck:webapp` here instead of
 * rendering as a blank cell on the one page whose entire job is to leave nothing unexplained.
 */
export type TraceOutcome = PracticeTraceEntry["outcome"];
export type ReviewTier = PracticeTraceEntry["reviewTier"];
export type WithheldReason = PracticeTraceEntry["withheldReasons"][number];
export type SignalState = TracedSignal["state"];
export type SignalStateReason = NonNullable<TracedSignal["stateReason"]>;
export type DiscoveredVia = TracedSignal["discoveredVia"];

/**
 * Was the practice *measured*. Deliberately says nothing about whether anyone heard about it —
 * that is `observationCount`/`deliveredCount`/`withheldReasons`, a separate axis. A practice can be
 * "Reviewed" and have delivered nothing, which is the MEASURE tier working exactly as configured.
 */
export const OUTCOME_LABELS: Record<TraceOutcome, string> = {
	REVIEWED: "Reviewed",
	RUNNING: "Running",
	PENDING: "Waiting",
	SKIPPED: "Skipped",
	NOT_ASSESSABLE: "Couldn't assess",
	SILENCED: "Turned off",
	NOT_OCCASIONED: "Not triggered",
	DORMANT: "Waiting on a connection",
	LAPSED: "Expired",
	FAILED: "Failed",
};

export const OUTCOMES = Object.keys(OUTCOME_LABELS) as TraceOutcome[];

/** How loudly the workspace runs a practice. The upper bound on what could ever reach a person. */
export const REVIEW_TIER_LABELS: Record<ReviewTier, string> = {
	OFF: "Off",
	MEASURE: "Measure only",
	COACH: "Coach",
	ENGAGE: "Engage",
};

export const REVIEW_TIER_DESCRIPTIONS: Record<ReviewTier, string> = {
	OFF: "Not run at all on this workspace.",
	MEASURE: "Measured for reporting, never spoken about.",
	COACH: "Measured, and notable results are raised with you.",
	ENGAGE: "Measured, raised with you, and followed up in conversation.",
};

export const SIGNAL_STATE_LABELS: Record<SignalState, string> = {
	RECORDED: "Recorded",
	TRIGGERED: "Started a review",
	SUPPRESSED: "No review started",
	PENDING: "Queued for review",
	LAPSED: "Expired before it was reviewed",
};

export const SIGNAL_STATE_REASON_LABELS: Record<SignalStateReason, string> = {
	GATE_SKIPPED: "A review gate on this workspace turned it away",
	COOLDOWN_ACTIVE: "This work was reviewed too recently",
	CONCURRENT_DUPLICATE: "The same review was already running",
	OUT_OF_REVIEW_SCOPE: "This repository or branch is outside the workspace's review scope",
	WORKSPACE_INACTIVE: "The workspace was not active",
	PRACTICES_DISABLED: "Practice reviews are switched off for this workspace",
	NO_ACTIVE_PRACTICE: "No practice was active for this kind of work",
	BINDING_DISABLED: "Every practice that watches this is switched off",
	PRACTICE_TIER_OFF: "The practices that watch this are set to off",
	BUDGET_EXHAUSTED: "The workspace's AI budget was used up",
	SUBJECT_UNLINKED: "The author is not linked to a Hephaestus account",
	MODEL_UNAVAILABLE: "No AI model was available",
	PENDING_DEADLINE_EXCEEDED: "It waited too long to be picked up",
	ARTIFACT_GONE: "The work no longer exists",
};

/** How we came to know about an occurrence. Sets how precise `occurredAt` can be. */
export const DISCOVERED_VIA_LABELS: Record<DiscoveredVia, string> = {
	EVENT: "Live event",
	SYNC: "Noticed during a sync",
	MANUAL: "Requested by hand",
	BACKFILL: "Backfill of past work",
};

export const DISCOVERED_VIA_DESCRIPTIONS: Record<DiscoveredVia, string> = {
	EVENT: "Reported by the provider as it happened, so the time is exact.",
	SYNC: "Spotted by a scheduled sync, so the time is only as precise as the sync.",
	MANUAL: "Someone asked for this review explicitly.",
	BACKFILL: "Recorded while catching up on work that predates the connection.",
};

/**
 * Withholding reasons are the same vocabulary the delivery surface already explains, so the copy is
 * borrowed rather than re-written — two different sentences for one enum value is how a support
 * answer and a screen stop agreeing. The `Record<WithheldReason, …>` annotation is load-bearing: it
 * fails the build if the trace endpoint ever reports a reason the delivery surface has no words for.
 */
export const WITHHELD_REASON_LABELS: Record<WithheldReason, string> = SUPPRESSION_REASON_LABELS;

/**
 * The DOM id of one occurrence in the timeline. The anchor a practice row links to and the element
 * it lands on both go through here, so the two can never drift apart.
 */
export function occurrenceDomId(signalId: string): string {
	return `occurrence-${signalId}`;
}

/**
 * Kinds are an open vocabulary — a kind this build has never heard of still gets a generic icon
 * rather than a hole, because a page about "what happened to my work" cannot be the page that
 * quietly omits a whole class of work.
 */
export function artifactKindIcon(kind: string) {
	switch (kind) {
		case ARTIFACT_KIND.pullRequest:
			return GitPullRequestIcon;
		case ARTIFACT_KIND.issue:
			return CircleDotIcon;
		case ARTIFACT_KIND.conversationThread:
			return MessagesSquareIcon;
		default:
			return FileTextIcon;
	}
}

/**
 * "3 signals · 1 reviewed". Says how much of what we saw actually turned into a review, which is the
 * first thing to check when the answer to "why was nothing said" is "we never started".
 */
export function signalCountsLabel(signalCount: number, reviewedSignalCount: number): string {
	const signals = `${signalCount} ${signalCount === 1 ? "signal" : "signals"}`;
	return `${signals} · ${reviewedSignalCount} reviewed`;
}

/**
 * The delivery axis in one phrase. Never collapsed into the outcome badge: "Reviewed" plus
 * "nothing was delivered" is a legitimate, configured state and the reader has to be able to see
 * both halves to know which one to go and change.
 */
export function deliveryLabel(entry: PracticeTraceEntry): string {
	if (entry.deliveredCount > 0) {
		return entry.deliveredCount === 1
			? "1 piece of feedback reached you"
			: `${entry.deliveredCount} pieces of feedback reached you`;
	}
	if (entry.observationCount === 0) {
		return "Nothing was measured, so nothing was sent";
	}
	const measured =
		entry.observationCount === 1 ? "1 measurement" : `${entry.observationCount} measurements`;
	return entry.withheldReasons.length > 0
		? `${measured}, none sent`
		: `${measured}, nothing needed saying`;
}
