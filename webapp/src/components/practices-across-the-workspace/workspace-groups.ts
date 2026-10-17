import type { GroupRef, OutcomeVector, PracticeWorkspaceTile } from "@/api/types.gen";

export interface WorkspaceGroup extends GroupRef {
	practices: PracticeWorkspaceTile[];
}

export function groupsOf(practices: readonly PracticeWorkspaceTile[]): WorkspaceGroup[] {
	const groups = new Map<string, WorkspaceGroup>();
	for (const practice of practices) {
		const group: GroupRef | null = practice.group;
		if (group === null) {
			continue;
		}
		const listed = groups.get(group.slug);
		if (listed === undefined) {
			groups.set(group.slug, { ...group, practices: [practice] });
		} else {
			listed.practices.push(practice);
		}
	}
	return [...groups.values()];
}

// Each result belongs to one practice, but one developer can have results in several practices.
export function outcomeTotal(vectors: readonly OutcomeVector[]): OutcomeVector {
	const total = { met: 0, notMet: 0, doesNotApply: 0, couldNotDetermine: 0 };
	for (const each of vectors) {
		total.met += each.met;
		total.notMet += each.notMet;
		total.doesNotApply += each.doesNotApply;
		total.couldNotDetermine += each.couldNotDetermine;
	}
	return total;
}
