import { CircleDotIcon, FileTextIcon, GitPullRequestIcon, MessagesSquareIcon } from "lucide-react";
import type { PracticeTraceEntry, TracedSignal } from "@/api/types.gen";
import { SUPPRESSION_REASON_LABELS } from "@/components/admin/practice-reviews/review-format";
import { ARTIFACT_KIND } from "@/lib/artifact-kinds";

/**
 * Labels key off the generated wire union rather than a hand-kept string list, so a value the
 * server adds or renames fails `typecheck:webapp` here instead of rendering as a blank cell.
 */
export type TraceOutcome = PracticeTraceEntry["outcome"];
export type ReviewTier = PracticeTraceEntry["reviewTier"];
export type WithheldReason = PracticeTraceEntry["withheldReasons"][number];
export type SignalState = TracedSignal["state"];
export type SignalStateReason = NonNullable<TracedSignal["stateReason"]>;
export type DiscoveredVia = TracedSignal["discoveredVia"];

/**
 * Was the practice *measured*, and nothing more: whether anyone heard about it is the separate
 * `observationCount`/`deliveredCount`/`withheldReasons` axis. "Reviewed" having delivered nothing
 * is the MEASURE tier working as configured.
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

/** The upper bound on what a practice could ever say to a person. */
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
	REQUEST_COOLDOWN_ACTIVE: "A review of this was already asked for a moment ago",
	REQUESTER_QUOTA_EXHAUSTED: "You have asked for as many reviews as an hour allows",
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

/** How we came to know about an occurrence, which sets how precise `occurredAt` can be. */
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
 * Borrowed from the delivery surface rather than re-worded: two different sentences for one enum
 * value is how a support answer and a screen stop agreeing. The `Record<WithheldReason, …>`
 * annotation fails the build if the trace endpoint reports a reason delivery has no words for.
 */
export const WITHHELD_REASON_LABELS: Record<WithheldReason, string> = SUPPRESSION_REASON_LABELS;

/** The anchor a practice row links to and the element it lands on both go through here. */
export function occurrenceDomId(signalId: string): string {
	return `occurrence-${signalId}`;
}

/**
 * Kinds are an open vocabulary: a kind this build has never heard of gets a generic icon rather
 * than a hole, on the page whose whole job is to omit nothing.
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

export function signalCountsLabel(signalCount: number, reviewedSignalCount: number): string {
	const signals = `${signalCount} ${signalCount === 1 ? "signal" : "signals"}`;
	return `${signals} · ${reviewedSignalCount} reviewed`;
}

/**
 * Never collapsed into the outcome badge: "Reviewed" with nothing delivered is a configured state,
 * and the reader has to see both halves to know which one to go and change.
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
