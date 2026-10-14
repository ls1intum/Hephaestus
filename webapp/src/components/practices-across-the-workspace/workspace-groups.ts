import type { GroupRef, OutcomeVector, PracticeWorkspaceTile } from "@/api/types.gen";

/** One practice group on Practices across the workspace, with its practices in the server's order. */
export interface WorkspaceGroup extends GroupRef {
	practices: PracticeWorkspaceTile[];
}

/**
 * The practices under their groups, each group where its first practice is. A practice in no
 * group is left out: the page lists practices only inside their group, as the Practice profile does.
 */
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

/**
 * The results of several practices added up. Each result belongs to one practice, so the sum counts
 * every result once. Developers do not add up the same way: one developer has results in many practices.
 */
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
