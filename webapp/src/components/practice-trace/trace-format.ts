import type { PracticeTraceEntry, TracedSignal } from "@/api/types.gen";
import { SUPPRESSION_REASON_LABELS } from "@/components/admin/practice-reviews/review-format";

/**
 * Labels key off the generated wire union rather than a hand-kept string list, so a value the
 * server adds or renames fails `typecheck:webapp` here instead of rendering as a blank cell.
 */
export type TraceOutcome = PracticeTraceEntry["outcome"];
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
	TURNED_OFF: "Turned off",
	NOT_OCCASIONED: "Not triggered",
	DORMANT: "Waiting on a connection",
	LAPSED: "Expired",
	FAILED: "Failed",
};

export const OUTCOMES = Object.keys(OUTCOME_LABELS) as TraceOutcome[];

export const SIGNAL_STATE_LABELS: Record<SignalState, string> = {
	RECORDED: "Recorded",
	TRIGGERED: "Started a review",
	SUPPRESSED: "No review started",
	PENDING: "Queued for review",
	LAPSED: "Expired before it was reviewed",
};

/**
 * Written in the third person throughout. Any member of the workspace can open this page, so the
 * occurrence being explained is usually somebody else's — "you have used your allowance" would tell
 * the wrong person off for a limit they never reached.
 */
export const SIGNAL_STATE_REASON_LABELS: Record<SignalStateReason, string> = {
	GATE_SKIPPED: "This workspace's review settings turned it away",
	COOLDOWN_ACTIVE: "This work was reviewed too recently; a later change gets its own review",
	REQUEST_COOLDOWN_ACTIVE: "A review of this was already asked for a moment ago",
	REQUESTER_QUOTA_EXHAUSTED: "Whoever asked had used up their hour's allowance, which refills",
	CONCURRENT_DUPLICATE: "The same review was already running",
	OUT_OF_REVIEW_SCOPE: "This repository or branch is outside the workspace's review scope",
	WORKSPACE_INACTIVE: "The workspace was not active",
	PRACTICES_DISABLED: "Practice reviews are switched off for this workspace",
	NO_ACTIVE_PRACTICE: "No practice was watching for this when it happened",
	REVIEW_MODEL_UNBOUND: "No AI model is set up to run reviews; choose one under AI models",
	PRACTICE_TIER_OFF: "Every practice watching this is turned off; raising one lets it run",
	BUDGET_EXHAUSTED: "The workspace's AI budget was used up; it refills",
	SUBJECT_UNLINKED: "The author has not linked their account to Hephaestus",
	MODEL_UNAVAILABLE: "The AI model set for reviews is no longer available",
	PENDING_DEADLINE_EXCEEDED: "It waited too long to be picked up",
	ARTIFACT_GONE: "The work no longer exists",
};

/** How we came to know about an occurrence, which sets how precise `occurredAt` can be. */
export const DISCOVERED_VIA_LABELS: Record<DiscoveredVia, string> = {
	EVENT: "Live event",
	SYNC: "Noticed during a sync",
	MANUAL: "Requested by hand",
	BACKFILL: "Backfill of past work",
	SWEEP: "Found by a recurring check",
};

export const DISCOVERED_VIA_DESCRIPTIONS: Record<DiscoveredVia, string> = {
	EVENT: "Reported by the provider as it happened, so the time is exact.",
	SYNC: "Spotted by a scheduled sync, so the time is only as precise as the sync.",
	MANUAL: "Someone asked for this review explicitly.",
	BACKFILL: "Recorded while catching up on work that predates the connection.",
	SWEEP:
		"Found by the recurring check over recent work, not announced by the provider — so the time is only as precise as the check.",
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
 * "Moment", not "signal": the authoring screens already call the thing a practice waits for a moment,
 * and the ledger's name for it belongs in the ledger. The second half counts the moments that started
 * a review, which is the number a reader is really asking about.
 */
export function signalCountsLabel(signalCount: number, reviewedSignalCount: number): string {
	const moments = signalCount === 1 ? "1 moment recorded" : `${signalCount} moments recorded`;
	return `${moments} · ${reviewedSignalCount} started a review`;
}

/**
 * Never collapsed into the outcome badge: "Reviewed" with nothing delivered is a configured state,
 * and the reader has to see both halves to know which one to go and change.
 *
 * <p>Third person, because any member of the workspace can open this page and the work is usually
 * somebody else's.
 */
export function deliveryLabel(entry: PracticeTraceEntry): string {
	if (entry.deliveredCount > 0) {
		return entry.deliveredCount === 1
			? "1 piece of feedback reached the developer"
			: `${entry.deliveredCount} pieces of feedback reached the developer`;
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
