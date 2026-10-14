import type { FeedbackModeAssignment, Practice, PracticeGroup } from "@/api/types.gen";
import { currentDefinition } from "@/components/admin/practices/practice-definition-fields";
import type { FeedbackMode, FeedbackModeLevel } from "@/lib/practice-feedback-mode";

export const UNASSIGNED_GROUP_KEY = "__unassigned__";

export interface FeedbackModeGroup {
	key: string;
	/** Null for the practices that sit in no group; that bucket holds no feedback mode of its own. */
	group: PracticeGroup | null;
	name: string;
	counts: Record<FeedbackMode, number>;
	overriddenCount: number;
	practices: Practice[];
	totalPractices: number;
}

/** Whether Heph reviews the practice. Reviewed by people or nobody is independent of feedback mode. */
export function reviewedByHephaestus(practice: Pick<Practice, "slug" | "currentVersion">): boolean {
	return currentDefinition(practice).reviewedBy.by === "HEPHAESTUS";
}

/** Chosen at this level rather than inherited from the level above. */
export function isOverridden(
	assignment: FeedbackModeAssignment,
	level: FeedbackModeLevel,
): boolean {
	return assignment.source === level;
}

export function practiceName(practice: Pick<Practice, "slug" | "currentVersion">): string {
	return currentDefinition(practice).name;
}

export interface GroupOptions {
	overridesOnly?: boolean;
}

export function groupPracticesByGroup(
	groups: readonly PracticeGroup[],
	practices: readonly Practice[],
	{ overridesOnly = false }: GroupOptions = {},
): FeedbackModeGroup[] {
	const byGroup = new Map<string, Practice[]>();
	for (const practice of [...practices].sort((a, b) => a.position - b.position)) {
		const key = practice.groupSlug ?? UNASSIGNED_GROUP_KEY;
		const bucket = byGroup.get(key);
		if (bucket) {
			bucket.push(practice);
		} else {
			byGroup.set(key, [practice]);
		}
	}

	const result: FeedbackModeGroup[] = [...groups]
		.sort((a, b) => a.position - b.position)
		.map((group) => bucketOf(group.slug, group, group.name, byGroup.get(group.slug) ?? []));
	const unassigned = byGroup.get(UNASSIGNED_GROUP_KEY);
	if (unassigned) {
		result.push(bucketOf(UNASSIGNED_GROUP_KEY, null, "Unassigned", unassigned));
	}

	if (!overridesOnly) {
		return result;
	}
	return result
		.map((bucket) => ({
			...bucket,
			practices: bucket.practices.filter((practice) =>
				isOverridden(practice.feedbackMode, "PRACTICE"),
			),
		}))
		.filter(
			(bucket) =>
				bucket.practices.length > 0 ||
				(bucket.group !== null && isOverridden(bucket.group.feedbackMode, "GROUP")),
		);
}

function bucketOf(
	key: string,
	group: PracticeGroup | null,
	name: string,
	practices: Practice[],
): FeedbackModeGroup {
	return {
		key,
		group,
		name,
		counts: countByFeedbackMode(practices),
		overriddenCount: practices.filter((practice) => isOverridden(practice.feedbackMode, "PRACTICE"))
			.length,
		practices,
		totalPractices: practices.length,
	};
}

function countByFeedbackMode(practices: readonly Practice[]): Record<FeedbackMode, number> {
	const counts: Record<FeedbackMode, number> = { OFF: 0, APPROVE_FIRST: 0, AUTOMATIC: 0 };
	for (const practice of practices) {
		counts[practice.feedbackMode.effective] += 1;
	}
	return counts;
}

export function countOverrides(
	groups: readonly PracticeGroup[],
	practices: readonly Practice[],
): { practices: number; groups: number } {
	return {
		practices: practices.filter((practice) => isOverridden(practice.feedbackMode, "PRACTICE"))
			.length,
		groups: groups.filter((group) => isOverridden(group.feedbackMode, "GROUP")).length,
	};
}
