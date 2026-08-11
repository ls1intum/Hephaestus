import type { Practice, PracticeReviewSettings, ReviewTierAssignment } from "@/api/types.gen";

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
	"PROPOSE",
	"DELIVER",
] as const satisfies readonly ReviewTier[];

export const REVIEW_TIER_LABELS: Record<ReviewTier, string> = {
	OFF: "Off",
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
	PROPOSE: "Reviewed and recorded. Nothing is sent.",
	DELIVER: "Reviewed, and feedback delivered without asking.",
};

/**
 * The same three tiers said as a ladder: what each rung *adds* to the one below it.
 *
 * <p>A second set rather than a rewrite of {@link REVIEW_TIER_DESCRIPTIONS}, because the two answer
 * different questions. The descriptions above answer "what is this practice doing" for a reader who
 * sees one tier alone; these answer "what changes if I move one step right" for a reader looking at
 * all three at once. Folding them together would leave the standalone tooltip saying "Adds…" with
 * nothing to add to.
 *
 * <p>Off is the floor and adds nothing, so it says what it removes instead. Propose does not promise a
 * draft to read: nothing is composed at that tier, so the sentence stops at what is recorded.
 */
export const REVIEW_TIER_ADDS: Record<ReviewTier, string> = {
	OFF: "Nothing runs. No review, no record, nothing said.",
	PROPOSE: "Adds the review. Every observation is recorded, and nothing is sent.",
	DELIVER: "Adds sending. Feedback goes out without waiting to be approved.",
};

/** How the workspace level is named to a reader, wherever a sentence has to point at it. */
export const WORKSPACE_DEFAULT_SOURCE = "the workspace default";

/**
 * Which level decided the tier in force, as the one sentence every screen prints.
 *
 * <p>Null when the tier was set on the thing itself, because the two screens honestly say different
 * things there: the one that can change it offers the way back, and the one that only reads it says
 * where to go instead. Only the shared half — the wording of "inherited, and from where" — lives here,
 * which is the half that drifts when it is written twice.
 *
 * <p>`inheritedFrom` is what the caller believes sits one level up, and it is deliberately not trusted
 * on its own: a practice in an area that holds no tier inherits the workspace default directly, and
 * naming the area there would send an admin to a level that decided nothing.
 */
export function inheritedTierSourceSentence(
	assignment: ReviewTierAssignment,
	inheritedFrom: string,
): string | null {
	if (!assignment.inherited) return null;
	const decidedBy = assignment.source === "WORKSPACE" ? WORKSPACE_DEFAULT_SOURCE : inheritedFrom;
	return `Follows ${decidedBy}`;
}

/** Where a workspace lets feedback go at all. ANDed with every tier, so it can only ever silence. */
export type FeedbackReach = PracticeReviewSettings["feedbackReach"];

/**
 * Narrowest first, so the pair reads the same way the tier ladder does — left is less.
 */
export const FEEDBACK_REACH_ORDER = [
	"CONVERSATION",
	"ON_THE_WORK",
] as const satisfies readonly FeedbackReach[];

export const FEEDBACK_REACH_LABELS: Record<FeedbackReach, string> = {
	CONVERSATION: "In the mentor conversation",
	ON_THE_WORK: "On the work as well",
};

/**
 * Neither sentence promises feedback: reach is ANDed with the tier, so it can stop a practice speaking
 * in a place but never make a quiet one speak. "Also" carries that in the wider of the two.
 */
export const FEEDBACK_REACH_DESCRIPTIONS: Record<FeedbackReach, string> = {
	CONVERSATION: "Feedback reaches the person in their mentor conversation and nowhere else.",
	ON_THE_WORK:
		"Feedback also lands on the work itself — pull request summaries, inline notes and issue comments.",
};

export interface ReviewTierCount {
	tier: ReviewTier;
	count: number;
}

/**
 * A tier count map as an ordered list, with the empty tiers dropped.
 *
 * <p>The rollup carries every tier as a key even at zero, which is what lets a caller render a
 * distribution without gap-filling — but printing "0 propose" on a workspace with none of them spends
 * the summary line on a number nobody asked about. Ladder order, not count order: an admin reading
 * "12 off · 84 propose" twice in a row should see the same shape both times.
 */
export function tierDistribution(counts: Record<string, number>): ReviewTierCount[] {
	return REVIEW_TIER_ORDER.map((tier) => ({ tier, count: counts[tier] ?? 0 })).filter(
		({ count }) => count > 0,
	);
}

export function tierTotal(counts: Record<string, number>): number {
	return REVIEW_TIER_ORDER.reduce((total, tier) => total + (counts[tier] ?? 0), 0);
}

/**
 * The distribution as one spoken sentence, for the live region that announces it.
 *
 * <p>The visible line separates counts with a middot, which a screen reader either skips or reads as
 * "middle dot"; neither is the sentence an admin needs to hear when a bulk change lands.
 */
export function tierDistributionSentence(counts: Record<string, number>): string {
	const parts = tierDistribution(counts).map(
		({ tier, count }) => `${count} ${REVIEW_TIER_LABELS[tier].toLowerCase()}`,
	);
	if (parts.length === 0) return "No practices yet.";
	const total = tierTotal(counts);
	const listed =
		parts.length === 1 ? parts[0] : `${parts.slice(0, -1).join(", ")} and ${parts.at(-1)}`;
	return `${total} ${total === 1 ? "practice" : "practices"}: ${listed}.`;
}
