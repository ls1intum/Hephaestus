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
	/** How many practices in this group hold a tier of their own. Server-counted, over the whole group. */
	overriddenCount: number;
	/** The rows to render — already narrowed by the active filter. */
	practices: Practice[];
	/** Practices in the group before filtering, so a narrowed group can say what it is hiding. */
	totalPractices: number;
}

/**
 * Mirrors the server's `canAttemptAutomatedReview`.
 *
 * <p>Duplicated rather than inferred from the tier, because the two say different things: a practice
 * the system cannot review is written to Off and refused above it, and a control that offered Propose
 * there would fail after the click with a message about a policy the admin is not looking at. The
 * server owns the rule; this only decides whether to offer the choice.
 */
export function reviewableByHephaestus(policy: PracticeAutomatedReviewPolicy): boolean {
	return (
		policy.automatedReview.mode === "LANGUAGE_MODEL" &&
		policy.automatedReview.evidenceSufficiency === "SUFFICIENT_WHEN_REQUIREMENTS_MET"
	);
}

/**
 * True when this row is somebody's deliberate decision rather than a value flowing down the chain.
 *
 * <p>Read off `inherited`, never off `source`. An area that set its own tier reports
 * `source: "AREA"` *and* `inherited: false`; deriving "inherited" from `source !== "PRACTICE"` marks
 * every such area as untouched and hides it from the overrides filter, which is the one view an admin
 * opens to find what they changed.
 */
export function isOverridden(assignment: ReviewTierAssignment): boolean {
	return !assignment.inherited;
}

export interface GroupOptions {
	/** Keep only the rows somebody deliberately set — the handful, out of a hundred. */
	overridesOnly?: boolean;
}

/**
 * The rollup's areas, in the server's catalogue order, each carrying its own practices.
 *
 * <p>Driven by the rollup rather than by the practice list: the counts and the ordering are the
 * server's, and recomputing them here would be a second implementation of the inheritance chain that
 * drifts on the first change. The practice list only supplies the rows.
 *
 * <p>Areas the rollup lists with no practices are kept — an empty area is still a place to park a
 * decision, and dropping it would make the area's own tier control unreachable. Practices whose area
 * the rollup does not know are kept too, in a trailing group of their own: the two queries can be a
 * moment out of step after a write, and a row that quietly disappears is worse than a row under a
 * heading that looks odd for one render.
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

	// An area that made its own decision stays even when none of its practices did: that decision is
	// exactly what the filter was opened to find, and its rows are the ones it applies to.
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
 * How many decisions this workspace holds of its own, across both levels.
 *
 * <p>`overriddenCount` is per area and counts practices only, so an admin who set three area tiers and
 * nothing else would read "0 set their own" and conclude the screen was broken.
 *
 * <p>Taken from the rollup rather than from the grouped rows: the rollup already counts every practice
 * in the workspace, while the groups carry only the ones a filter left standing — and asking the
 * groups would mean grouping a hundred practices a second time on every render just to add up two
 * numbers the server already sent.
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
