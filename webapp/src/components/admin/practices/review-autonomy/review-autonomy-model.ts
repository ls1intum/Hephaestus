import type {
	Practice,
	PracticeAutomatedReviewPolicy,
	ReviewTierAssignment,
	ReviewTierRollup,
} from "@/api/types.gen";

/** The rollup groups practices that belong to no area under a null slug; a React key cannot be null. */
export const UNASSIGNED_AREA_KEY = "__unassigned__";

export interface AutonomyGroup {
	key: string;
	/** Null for the group of practices that belong to no area. */
	areaSlug: string | null;
	name: string;
	/** The area's own tier. For the no-area group this is the workspace's answer, which is not editable here. */
	reviewTier: ReviewTierAssignment;
	counts: Record<string, number>;
	/** Practices in this group holding a tier of their own — server-counted, over the whole group. */
	overriddenCount: number;
	practices: Practice[];
	/** Practices in the group before filtering, so a narrowed group can say what it is hiding. */
	totalPractices: number;
}

/**
 * Mirrors the server's `canAttemptAutomatedReview`, which owns the rule; this only decides whether to
 * offer the choice. Not inferable from the tier: a practice the system cannot review is written to
 * Off and refused above it, so offering Propose would fail after the click.
 */
export function reviewableByHephaestus(policy: PracticeAutomatedReviewPolicy): boolean {
	return (
		policy.automatedReview.mode === "LANGUAGE_MODEL" &&
		policy.automatedReview.evidenceSufficiency === "SUFFICIENT_WHEN_REQUIREMENTS_MET"
	);
}

/**
 * Read off `inherited`, never off `source`: an area that set its own tier reports `source: "AREA"`
 * *and* `inherited: false`, so deriving this from `source` marks every such area as untouched and
 * hides it from the overrides filter.
 */
export function isOverridden(assignment: ReviewTierAssignment): boolean {
	return !assignment.inherited;
}

export interface GroupOptions {
	/** Keep only the rows somebody deliberately set. */
	overridesOnly?: boolean;
}

/**
 * Driven by the rollup rather than by the practice list: the counts and the catalogue ordering are the
 * server's, and recomputing them here would be a second implementation of the inheritance chain. The
 * practice list only supplies the rows.
 *
 * An area the rollup lists with no practices is kept, or its own tier control would be unreachable.
 * A practice whose area the rollup does not know is kept too, in a trailing group: the two queries can
 * be a moment out of step after a write, and a row that silently disappears is the worse failure.
 */
export function groupPracticesByArea(
	rollup: ReviewTierRollup,
	practices: readonly Practice[],
	{ overridesOnly = false }: GroupOptions = {},
): AutonomyGroup[] {
	const byArea = new Map<string, Practice[]>();
	for (const practice of practices) {
		const key = practice.areaSlug ?? UNASSIGNED_AREA_KEY;
		const bucket = byArea.get(key);
		if (bucket) bucket.push(practice);
		else byArea.set(key, [practice]);
	}

	const groups: AutonomyGroup[] = [];
	for (const area of rollup.areas) {
		const key = area.areaSlug ?? UNASSIGNED_AREA_KEY;
		const owned = byArea.get(key) ?? [];
		byArea.delete(key);
		groups.push({
			key,
			areaSlug: area.areaSlug ?? null,
			name: area.areaName ?? "Not in an area",
			reviewTier: area.reviewTier,
			counts: area.counts,
			overriddenCount: area.overriddenCount,
			practices: owned,
			totalPractices: owned.length,
		});
	}

	for (const [key, owned] of byArea) {
		groups.push({
			key,
			areaSlug: key === UNASSIGNED_AREA_KEY ? null : key,
			name: key === UNASSIGNED_AREA_KEY ? "Not in an area" : key,
			reviewTier: rollup.workspaceDefault,
			counts: countByTier(owned),
			overriddenCount: owned.filter((practice) => isOverridden(practice.reviewTier)).length,
			practices: owned,
			totalPractices: owned.length,
		});
	}

	if (!overridesOnly) return groups;

	// An area that made its own decision stays even when none of its practices did — that decision is
	// what the filter was opened to find.
	return groups
		.map((group) => ({
			...group,
			practices: group.practices.filter((practice) => isOverridden(practice.reviewTier)),
		}))
		.filter(
			(group) =>
				group.practices.length > 0 || (group.areaSlug !== null && isOverridden(group.reviewTier)),
		);
}

/** Client-side counting, for the one group the server does not know about yet. */
function countByTier(practices: readonly Practice[]): Record<string, number> {
	const counts: Record<string, number> = {};
	for (const practice of practices) {
		const tier = practice.reviewTier.effective;
		counts[tier] = (counts[tier] ?? 0) + 1;
	}
	return counts;
}

/**
 * Areas and practices are counted separately because `overriddenCount` is per area and counts
 * practices only — an admin who set area tiers and nothing else would otherwise read "0 set by hand".
 *
 * Taken from the rollup, not from the grouped rows: the groups carry only what a filter left standing.
 */
export function countOverrides(rollup: ReviewTierRollup): {
	practices: number;
	areas: number;
} {
	return {
		practices: rollup.areas.reduce((total, area) => total + area.overriddenCount, 0),
		areas: rollup.areas.filter((area) => area.areaSlug != null && isOverridden(area.reviewTier))
			.length,
	};
}
