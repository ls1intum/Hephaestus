import type { Practice, ReviewTierAssignment } from "@/api/types.gen";

export type ReviewTier = Practice["reviewTier"]["effective"];

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

export const REVIEW_TIER_DESCRIPTIONS: Record<ReviewTier, string> = {
	OFF: "Not reviewed at all.",
	PROPOSE: "Reviewed and recorded. Nothing is sent.",
	DELIVER: "Reviewed, and feedback delivered without asking.",
};

export const REVIEW_TIER_ADDS: Record<ReviewTier, string> = {
	OFF: "Nothing runs. No review, no record, nothing said.",
	PROPOSE: "Adds the review. Every observation is recorded, and nothing is sent.",
	DELIVER: "Adds sending. Feedback goes out without waiting to be approved.",
};

export const WORKSPACE_DEFAULT_SOURCE = "the workspace default";

export function inheritedTierSourceSentence(
	assignment: ReviewTierAssignment,
	inheritedFrom: string | null,
): string | null {
	if (!assignment.inherited) return null;
	if (assignment.source === "WORKSPACE") return `Follows ${WORKSPACE_DEFAULT_SOURCE}`;
	return `Follows ${inheritedFrom ?? "its area"}`;
}

export interface ReviewTierCount {
	tier: ReviewTier;
	count: number;
}

export function tierDistribution(counts: Record<string, number>): ReviewTierCount[] {
	return REVIEW_TIER_ORDER.map((tier) => ({ tier, count: counts[tier] ?? 0 })).filter(
		({ count }) => count > 0,
	);
}

export function tierTotal(counts: Record<string, number>): number {
	return REVIEW_TIER_ORDER.reduce((total, tier) => total + (counts[tier] ?? 0), 0);
}

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
