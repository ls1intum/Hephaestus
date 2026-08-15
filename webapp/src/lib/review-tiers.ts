import type { Practice, PracticeReviewSettings, ReviewTierAssignment } from "@/api/types.gen";

/**
 * How much autonomy the system has over one practice, in the words every surface has to use.
 *
 * Derived from `effective`, not from `reviewTier` itself: the API reports a tier as an assignment —
 * the tier in force, the raw override, the level that decided it, whether it was inherited — because
 * a screen renders "inherited, with a reset" differently from "set here". This vocabulary is about the
 * tier alone.
 */
export type ReviewTier = Practice["reviewTier"]["effective"];

/**
 * Ascending autonomy, and load-bearing: every surface lays the tiers out in this order. Each tier
 * above Off runs the review and they differ only in how far the system may act on its own — where
 * feedback goes is the separate, workspace-level {@link FeedbackReach}.
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
 * Two constraints on anything written here. Each sentence has to stand alone, because a surface may
 * show one tier with nothing to compare it against; and none of them may say *where* feedback goes,
 * which is the reach setting and not this one.
 *
 * One short line each: these are printed inside a menu that is already tall, and a sentence long
 * enough to wrap turns it into a scrollable region a keyboard cannot reach, which the a11y gate fails.
 */
export const REVIEW_TIER_DESCRIPTIONS: Record<ReviewTier, string> = {
	OFF: "Not reviewed at all.",
	PROPOSE: "Reviewed and recorded. Nothing is sent.",
	DELIVER: "Reviewed, and feedback delivered without asking.",
};

/**
 * What each rung *adds* to the one below it — a second set beside {@link REVIEW_TIER_DESCRIPTIONS}
 * rather than a rewrite of it, because these are only readable next to all three at once and those
 * have to work for a reader seeing one tier alone.
 *
 * Propose must not promise a draft to read: nothing is composed at that tier.
 */
export const REVIEW_TIER_ADDS: Record<ReviewTier, string> = {
	OFF: "Nothing runs. No review, no record, nothing said.",
	PROPOSE: "Adds the review. Every observation is recorded, and nothing is sent.",
	DELIVER: "Adds sending. Feedback goes out without waiting to be approved.",
};

/** How the workspace level is named to a reader, wherever a sentence has to point at it. */
export const WORKSPACE_DEFAULT_SOURCE = "the workspace default";

/**
 * Which level decided the tier in force. Null when the tier was set on the thing itself, because what
 * a screen offers there differs — the one that can change it offers a reset, the one that only reads
 * it says where to go.
 *
 * `inheritedFrom` supplies a name and never decides which level is named: a practice under an area
 * that holds no tier inherits the workspace default directly, so naming the area would point an admin
 * at a level that decided nothing. The assignment's `source` decides.
 *
 * A null `inheritedFrom` means the caller cannot name that level, which the two queries behind a
 * catalogue can be for one render after a write. It degrades to "its area" and not to the workspace:
 * on a row the server says an *area* decided, the workspace answer is wrong rather than vaguer.
 */
export function inheritedTierSourceSentence(
	assignment: ReviewTierAssignment,
	inheritedFrom: string | null,
): string | null {
	if (!assignment.inherited) return null;
	if (assignment.source === "WORKSPACE") return `Follows ${WORKSPACE_DEFAULT_SOURCE}`;
	return `Follows ${inheritedFrom ?? "its area"}`;
}

/** Where a workspace lets feedback go at all. ANDed with every tier, so it can only ever silence. */
export type FeedbackReach = PracticeReviewSettings["feedbackReach"];

/** Narrowest first, so the pair reads the way the tier ladder does — left is less. */
export const FEEDBACK_REACH_ORDER = [
	"CONVERSATION",
	"ON_THE_WORK",
] as const satisfies readonly FeedbackReach[];

export const FEEDBACK_REACH_LABELS: Record<FeedbackReach, string> = {
	CONVERSATION: "In the mentor conversation",
	ON_THE_WORK: "On the work as well",
};

/**
 * Neither sentence may promise feedback: reach is ANDed with the tier, so it can stop a practice
 * speaking in a place but never make a quiet one speak.
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
 * The rollup carries every tier as a key even at zero, so a caller never gap-fills — but the zeroes
 * are dropped here rather than printed. Ladder order, not count order, so the shape of the line does
 * not move when the numbers do.
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
 * A sentence rather than a separated list, because this is read aloud: it is the content of the live
 * region that announces the distribution after a bulk change.
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
