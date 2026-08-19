import type {
	AutonomyAssignment,
	AutonomyRollup,
	Practice,
	PracticeAutomatedReviewPolicy,
} from "@/api/types.gen";

export const UNASSIGNED_AREA_KEY = "__unassigned__";

export interface AutonomyGroup {
	key: string;
	areaSlug: string | null;
	name: string;
	autonomy: AutonomyAssignment;
	counts: Record<string, number>;
	overriddenCount: number;
	practices: Practice[];
	totalPractices: number;
}

export function reviewableByHephaestus(policy: PracticeAutomatedReviewPolicy): boolean {
	return (
		policy.automatedReview.mode === "LANGUAGE_MODEL" &&
		policy.automatedReview.evidenceSufficiency === "SUFFICIENT_WHEN_REQUIREMENTS_MET"
	);
}

export function isOverridden(assignment: AutonomyAssignment): boolean {
	return !assignment.inherited;
}

export interface GroupOptions {
	overridesOnly?: boolean;
}

export function groupPracticesByArea(
	rollup: AutonomyRollup,
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
			autonomy: area.autonomy,
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
			autonomy: rollup.workspaceDefault,
			counts: countByAutonomy(owned),
			overriddenCount: owned.filter((practice) => isOverridden(practice.autonomy)).length,
			practices: owned,
			totalPractices: owned.length,
		});
	}

	if (!overridesOnly) return groups;

	return groups
		.map((group) => ({
			...group,
			practices: group.practices.filter((practice) => isOverridden(practice.autonomy)),
		}))
		.filter(
			(group) =>
				group.practices.length > 0 || (group.areaSlug !== null && isOverridden(group.autonomy)),
		);
}

function countByAutonomy(practices: readonly Practice[]): Record<string, number> {
	const counts: Record<string, number> = {};
	for (const practice of practices) {
		const autonomy = practice.autonomy.effective;
		counts[autonomy] = (counts[autonomy] ?? 0) + 1;
	}
	return counts;
}

export function countOverrides(rollup: AutonomyRollup): {
	practices: number;
	areas: number;
} {
	return {
		practices: rollup.areas.reduce((total, area) => total + area.overriddenCount, 0),
		areas: rollup.areas.filter((area) => area.areaSlug != null && isOverridden(area.autonomy))
			.length,
	};
}
