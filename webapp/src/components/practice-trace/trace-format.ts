import type { PracticeTraceEntry, ReviewRequestOutcome, TracedSignal } from "@/api/types.gen";
import type { ReviewSectionId } from "@/components/admin/practices/review/review-sections";
import { statusValues } from "@/components/practice-vocabulary/status-def";
import {
	TRACE_OUTCOME_DEFS,
	type TraceOutcome,
} from "@/components/practice-vocabulary/trace-outcome-defs";
import { WITHHOLDING_REASON_DEFS } from "@/components/practice-vocabulary/withholding-defs";

/**
 * Labels key off the generated wire union rather than a hand-kept string list, so a value the
 * server adds or renames fails `typecheck:webapp` here instead of rendering as a blank cell.
 */
export type { TraceOutcome };
export type WithheldReason = PracticeTraceEntry["withheldReasons"][number];
export type SignalState = TracedSignal["state"];
export type SignalStateReason = NonNullable<TracedSignal["stateReason"]>;
export type DiscoveredVia = TracedSignal["discoveredVia"];

/**
 * The outcomes in the order a reader should meet them, which is also the filter's order.
 *
 * <p>Their words, icons and colours live in {@link TRACE_OUTCOME_DEFS} with every other status
 * registry — this file keeps only the vocabularies the trace does not share with anything else.
 */
export const OUTCOMES = statusValues(TRACE_OUTCOME_DEFS);

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
	// States the fact and stops. It used to end "; choose one under AI models", which named a page
	// while linking to nothing and told every member of the workspace to go and do something only an
	// admin can. The instruction now travels with the link, to the readers who can act on it.
	REVIEW_MODEL_UNBOUND: "No AI model is set up to run reviews",
	PRACTICE_TIER_OFF: "Every practice watching this is turned off; raising one lets it run",
	BUDGET_EXHAUSTED: "The workspace's AI budget was used up; it refills",
	SUBJECT_UNLINKED: "The author has not linked their account to Hephaestus",
	MODEL_UNAVAILABLE: "The AI model set for reviews is no longer available",
	PENDING_DEADLINE_EXCEEDED: "It waited too long to be picked up",
	ARTIFACT_GONE: "The work no longer exists",
};

/**
 * The same vocabulary answers "why did this occurrence go nowhere" and "why was my request refused",
 * so one fix table serves both.
 *
 * <p>The two unions are generated separately, and their agreement is asserted rather than assumed —
 * a reason added to only one of them fails the typecheck on the two lines below, naming the pair
 * that diverged, instead of quietly losing its link on one of the two surfaces.
 */
type RequestRefusalReason = NonNullable<ReviewRequestOutcome["reason"]>;

/** Resolves to `true` only while the two unions are mutually assignable. */
type Agree<A, B> = [A] extends [B] ? ([B] extends [A] ? true : false) : false;

/**
 * The assertion itself. `false` does not satisfy `extends true`, so divergence is a compile error on
 * this line rather than a silent collapse somewhere downstream.
 */
export type ReasonVocabulariesAgree<
	T extends true = Agree<RequestRefusalReason, SignalStateReason>,
> = T;

export type RefusalReason = SignalStateReason;

/**
 * Names the destination on its own. A link is read out of its sentence — by a screen reader listing
 * the page's links, and by anyone scanning — so "here" identifies nothing (WCAG 2.4.4).
 */
interface RefusalFixLabel {
	label: string;
}

/**
 * A destination as a link can be built from it, in the two shapes the app actually has: a plain
 * admin route that takes only `workspaceSlug`, or a section of the Review page, which is one route
 * plus a search param. Split rather than an optional `search`, so a fix cannot name a section of a
 * page that has none.
 */
export type RefusalFix = RefusalFixLabel &
	(
		| {
				to:
					| "/w/$workspaceSlug/admin/models"
					| "/w/$workspaceSlug/admin/practices"
					| "/w/$workspaceSlug/admin/settings"
					| "/w/$workspaceSlug/admin/usage";
				section?: never;
		  }
		| { to?: never; section: ReviewSectionId }
	);

/**
 * Where each refusal is undone, for the readers who can undo it.
 *
 * <p>Deliberately partial, and the gaps are the point. A reason earns an entry only when a workspace
 * admin can go somewhere and change the answer; the rest are either self-healing (a cooldown that
 * expires, an allowance that refills), already-running duplicates, or terminal. Sending someone to a
 * settings page that cannot affect what they just read is worse than sending them nowhere, because
 * they will change something to make it stop.
 *
 * <p>Three near misses, each with a destination that looks right and is not:
 *
 * <p>`SUBJECT_UNLINKED` is fixable, but only by the author, on their own account page — and the
 * reader of a trace is usually not the author, so there is no one destination to offer.
 *
 * <p>`COOLDOWN_ACTIVE` has a configurable interval, but an active cooldown is the setting working
 * rather than a fault, and a link there invites an admin to widen a limit to fix a non-fault.
 *
 * <p>`WORKSPACE_INACTIVE` is lifted by reactivating the workspace, which the webapp has no control
 * for at all: nothing outside the generated client calls the status endpoint, and the workspace
 * settings page offers feature toggles and a delete button. Linking there would land an admin on a
 * screen whose only lifecycle action is destroying the thing they came to restore.
 */
export const REFUSAL_FIXES: Partial<Record<SignalStateReason, RefusalFix>> = {
	GATE_SKIPPED: { section: "when-and-where", label: "Open Review: When and where" },
	OUT_OF_REVIEW_SCOPE: { section: "when-and-where", label: "Open Review: When and where" },
	PRACTICES_DISABLED: { section: "when-and-where", label: "Open Review: When and where" },
	PRACTICE_TIER_OFF: { section: "how-much", label: "Open Review: How much" },
	NO_ACTIVE_PRACTICE: { to: "/w/$workspaceSlug/admin/practices", label: "Open Practice setup" },
	REVIEW_MODEL_UNBOUND: { to: "/w/$workspaceSlug/admin/models", label: "Set up a review model" },
	MODEL_UNAVAILABLE: { to: "/w/$workspaceSlug/admin/models", label: "Open AI models" },
	BUDGET_EXHAUSTED: { to: "/w/$workspaceSlug/admin/usage", label: "Open AI usage" },
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
export const WITHHELD_REASON_LABELS: Record<WithheldReason, string> = WITHHOLDING_REASON_DEFS;

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
