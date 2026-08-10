import type { Practice } from "@/api/types.gen";

/**
 * How loud one practice is allowed to be in one workspace, in the words every surface has to use.
 *
 * <p>One module rather than a copy per screen. The catalog is where an admin sets the tier and the
 * artifact trace is where a developer reads it back, and the two shipped the same tier under two
 * names — "Measure" in one and "Measure only" in the other — which is how a screen and a support
 * answer stop agreeing about a setting nobody changed.
 */
export type ReviewTier = Practice["reviewTier"];

/** Ascending loudness. Every tier above Off runs the review; they differ only in who is told. */
export const REVIEW_TIER_ORDER = [
	"OFF",
	"MEASURE",
	"COACH",
	"ENGAGE",
] as const satisfies readonly ReviewTier[];

export const REVIEW_TIER_LABELS: Record<ReviewTier, string> = {
	OFF: "Off",
	MEASURE: "Measure",
	COACH: "Coach",
	ENGAGE: "Engage",
};

/**
 * Each sentence stands on its own, because one of the two surfaces shows a single tier in a tooltip
 * with nothing to compare it against. A ladder that only reads top-down ("Also raised in…") says
 * nothing there.
 *
 * <p>`COACH` is the mentor conversation and nothing else, and `ENGAGE` adds the work itself on top of
 * it. Saying it the other way round tells an admin that turning a practice down to `COACH` will stop
 * the mentor conversation, which is the opposite of what happens.
 *
 * <p>Kept to one short line each. The catalog prints these beside the tier name inside a menu that is
 * already tall; a sentence long enough to wrap turns that menu into a scrollable region a keyboard
 * cannot reach, which the a11y check fails.
 */
export const REVIEW_TIER_DESCRIPTIONS: Record<ReviewTier, string> = {
	OFF: "Not reviewed at all.",
	MEASURE: "Reviewed and recorded. Nobody is told.",
	COACH: "Reviewed, and raised in the mentor conversation.",
	ENGAGE: "Reviewed, raised in the conversation, and on the work.",
};
