import type { Practice } from "@/api/types.gen";

/**
 * How much autonomy the system has over one practice, in the words every surface has to use.
 *
 * <p>One module rather than a copy per screen. The catalog is where an admin sets the tier and the
 * artifact trace is where a developer reads it back, and the two shipped the same tier under two
 * names — "Measure" in one and "Measure only" in the other — which is how a screen and a support
 * answer stop agreeing about a setting nobody changed.
 *
 * <p>Derived from the `effective` field, not from `reviewTier` itself: the API reports a tier as an
 * assignment — the tier in force, the raw override, the level that decided it, and whether it was
 * inherited — because a screen has to render "inherited, de-emphasised, with a reset" differently from
 * "set here". The vocabulary below is about the tier alone.
 */
export type ReviewTier = Practice["reviewTier"]["effective"];

/**
 * Ascending autonomy. Every tier above Off runs the review; they differ in how far the system may act
 * on its own. Where feedback goes is a separate, workspace-level setting.
 */
export const REVIEW_TIER_ORDER = [
	"OFF",
	"OBSERVE",
	"PROPOSE",
	"DELIVER",
] as const satisfies readonly ReviewTier[];

/**
 * Propose is in the ladder but cannot be chosen yet: there is no queue for a human to approve the
 * feedback it would prepare, so a practice parked there would prepare feedback nobody can approve and
 * swallow it. The server refuses it at every write boundary; a surface that offers a tier picker has to
 * disable this one rather than let the choice fail after the click.
 */
export const REVIEW_TIER_SELECTABLE: Record<ReviewTier, boolean> = {
	OFF: true,
	OBSERVE: true,
	PROPOSE: false,
	DELIVER: true,
};

export const REVIEW_TIER_LABELS: Record<ReviewTier, string> = {
	OFF: "Off",
	OBSERVE: "Observe",
	PROPOSE: "Propose",
	DELIVER: "Deliver",
};

/**
 * Each sentence stands on its own, because one of the two surfaces shows a single tier in a tooltip
 * with nothing to compare it against. A ladder that only reads top-down ("Also raised in…") says
 * nothing there.
 *
 * <p>None of them says *where* feedback goes. That is the workspace's reach setting, and folding it in
 * here would tell an admin that turning a practice down changes where the system speaks, when it
 * changes whether it speaks at all.
 *
 * <p>Kept to one short line each. The catalog prints these beside the tier name inside a menu that is
 * already tall; a sentence long enough to wrap turns that menu into a scrollable region a keyboard
 * cannot reach, which the a11y check fails.
 */
export const REVIEW_TIER_DESCRIPTIONS: Record<ReviewTier, string> = {
	OFF: "Not reviewed at all.",
	OBSERVE: "Reviewed and recorded. Nobody is told.",
	PROPOSE: "Feedback prepared for a person to approve. Not available yet.",
	DELIVER: "Reviewed, and feedback delivered without asking.",
};
