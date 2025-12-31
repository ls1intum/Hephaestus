/**
 * Team hierarchy utilities for managing parent-child relationships,
 * member counting with rollup modes, and cycle detection.
 */

/** Minimal team structure needed for hierarchy operations */
export interface HierarchyTeam {
	id: number;
	parentId?: number;
	members: Array<{ id: number }>;
}

/** Member counting mode */
export type MemberCountMode = "direct" | "rollup";

/**
 * Builds a map from team ID to its direct children.
 * @param teams - Array of teams with id and parentId
 * @returns Map from parent ID to array of child teams
 */
export function buildChildrenMap<T extends HierarchyTeam>(teams: T[]): Map<number, T[]> {
	const teamIds = new Set(teams.map((t) => t.id));
	const map = new Map<number, T[]>();

	for (const team of teams) {
		const parentId = team.parentId;
		if (parentId !== undefined && teamIds.has(parentId)) {
			const children = map.get(parentId) ?? [];
			children.push(team);
			map.set(parentId, children);
		}
	}

	return map;
}

/**
 * Identifies root teams (teams without a valid parent in the dataset).
 * @param teams - Array of teams
 * @returns Array of root teams
 */
export function findRootTeams<T extends HierarchyTeam>(teams: T[]): T[] {
	const teamIds = new Set(teams.map((t) => t.id));
	return teams.filter((t) => t.parentId === undefined || !teamIds.has(t.parentId));
}

/**
 * Detects if there's a cycle in the team hierarchy starting from a given team.
 * Uses Floyd's cycle detection algorithm (tortoise and hare).
 * @param teamId - Starting team ID
 * @param teamsById - Map of team ID to team
 * @returns true if a cycle is detected
 */
export function hasCycle<T extends HierarchyTeam>(
	teamId: number,
	teamsById: Map<number, T>,
): boolean {
	const visited = new Set<number>();
	let current: number | undefined = teamId;

	while (current !== undefined) {
		if (visited.has(current)) {
			return true;
		}
		visited.add(current);
		const team = teamsById.get(current);
		current = team?.parentId;
	}

	return false;
}

/**
 * Gets all descendant team IDs for a given team, with cycle detection.
 * @param teamId - Team ID to get descendants for
 * @param childrenMap - Map from parent ID to children
 * @param visited - Set of already visited IDs (for cycle detection)
 * @returns Set of descendant team IDs (not including the team itself)
 */
export function getDescendantIds<T extends HierarchyTeam>(
	teamId: number,
	childrenMap: Map<number, T[]>,
	visited: Set<number> = new Set(),
): Set<number> {
	const result = new Set<number>();

	// Prevent cycles
	if (visited.has(teamId)) {
		return result;
	}
	visited.add(teamId);

	const children = childrenMap.get(teamId) ?? [];
	for (const child of children) {
		result.add(child.id);
		const grandchildren = getDescendantIds(child.id, childrenMap, visited);
		for (const id of grandchildren) {
			result.add(id);
		}
	}

	return result;
}

/**
 * Collects unique member IDs from a team and all its descendants.
 * Handles cycles by tracking visited teams.
 * @param teamId - Team ID to collect members from
 * @param teamsById - Map of team ID to team
 * @param childrenMap - Map from parent ID to children
 * @param visited - Set of already visited team IDs (for cycle detection)
 * @returns Set of unique member IDs
 */
export function collectRollupMemberIds<T extends HierarchyTeam>(
	teamId: number,
	teamsById: Map<number, T>,
	childrenMap: Map<number, T[]>,
	visited: Set<number> = new Set(),
): Set<number> {
	const result = new Set<number>();

	// Prevent cycles
	if (visited.has(teamId)) {
		return result;
	}
	visited.add(teamId);

	const team = teamsById.get(teamId);
	if (!team) {
		return result;
	}

	// Add direct members
	for (const member of team.members) {
		result.add(member.id);
	}

	// Recursively add members from children
	const children = childrenMap.get(teamId) ?? [];
	for (const child of children) {
		const childMembers = collectRollupMemberIds(child.id, teamsById, childrenMap, visited);
		for (const id of childMembers) {
			result.add(id);
		}
	}

	return result;
}

/**
 * Computes member counts for all teams in both direct and rollup modes.
 * Rollup mode counts unique members from the team and all descendants.
 * @param teams - Array of teams
 * @returns Map from team ID to { direct: number, rollup: number }
 */
export function computeMemberCounts<T extends HierarchyTeam>(
	teams: T[],
): Map<number, { direct: number; rollup: number }> {
	const teamsById = new Map<number, T>();
	for (const team of teams) {
		teamsById.set(team.id, team);
	}

	const childrenMap = buildChildrenMap(teams);
	const result = new Map<number, { direct: number; rollup: number }>();

	// Cache for rollup counts to avoid recomputation
	const rollupCache = new Map<number, Set<number>>();

	const getRollupMembers = (teamId: number): Set<number> => {
		const cached = rollupCache.get(teamId);
		if (cached !== undefined) {
			return cached;
		}

		const members = collectRollupMemberIds(teamId, teamsById, childrenMap, new Set());
		rollupCache.set(teamId, members);
		return members;
	};

	for (const team of teams) {
		const direct = team.members.length;
		const rollup = getRollupMembers(team.id).size;
		result.set(team.id, { direct, rollup });
	}

	return result;
}

/**
 * Gets the member count for a team based on the selected mode.
 * @param team - The team to count members for
 * @param mode - "direct" or "rollup"
 * @param memberCounts - Precomputed member counts (optional, will compute if not provided)
 * @param teams - All teams (required if memberCounts not provided)
 * @returns Member count
 */
export function getMemberCount<T extends HierarchyTeam>(
	team: T,
	mode: MemberCountMode,
	memberCounts?: Map<number, { direct: number; rollup: number }>,
	teams?: T[],
): number {
	if (mode === "direct") {
		return team.members.length;
	}

	if (memberCounts) {
		const counts = memberCounts.get(team.id);
		return counts?.rollup ?? team.members.length;
	}

	if (teams) {
		const counts = computeMemberCounts(teams);
		return counts.get(team.id)?.rollup ?? team.members.length;
	}

	// Fallback to direct count
	return team.members.length;
}

/**
 * Validates the team hierarchy and returns any issues found.
 * @param teams - Array of teams to validate
 * @returns Array of validation issues
 */
export function validateHierarchy<T extends HierarchyTeam>(
	teams: T[],
): Array<{ teamId: number; issue: string }> {
	const issues: Array<{ teamId: number; issue: string }> = [];
	const teamsById = new Map<number, T>();

	for (const team of teams) {
		teamsById.set(team.id, team);
	}

	for (const team of teams) {
		// Check for orphaned parent references
		if (team.parentId !== undefined && !teamsById.has(team.parentId)) {
			issues.push({
				teamId: team.id,
				issue: `Parent team ${team.parentId} not found`,
			});
		}

		// Check for self-reference
		if (team.parentId === team.id) {
			issues.push({
				teamId: team.id,
				issue: "Team references itself as parent",
			});
		}

		// Check for cycles
		if (hasCycle(team.id, teamsById)) {
			issues.push({
				teamId: team.id,
				issue: "Team is part of a hierarchy cycle",
			});
		}
	}

	return issues;
}
