import { describe, expect, it } from "vitest";
import {
	buildChildrenMap,
	collectRollupMemberIds,
	computeMemberCounts,
	findRootTeams,
	getDescendantIds,
	getMemberCount,
	type HierarchyTeam,
	hasCycle,
	validateHierarchy,
} from "./team-hierarchy";

// Helper to create test teams
function createTeam(id: number, parentId: number | undefined, memberIds: number[]): HierarchyTeam {
	return {
		id,
		parentId,
		members: memberIds.map((memberId) => ({ id: memberId })),
	};
}

describe("team-hierarchy", () => {
	describe("buildChildrenMap", () => {
		it("builds correct parent-child mappings", () => {
			const teams = [
				createTeam(1, undefined, []),
				createTeam(2, 1, []),
				createTeam(3, 1, []),
				createTeam(4, 2, []),
			];

			const map = buildChildrenMap(teams);

			expect(map.get(1)?.map((t) => t.id)).toEqual([2, 3]);
			expect(map.get(2)?.map((t) => t.id)).toEqual([4]);
			expect(map.has(3)).toBe(false);
			expect(map.has(4)).toBe(false);
		});

		it("ignores orphaned parent references", () => {
			const teams = [
				createTeam(1, undefined, []),
				createTeam(2, 99, []), // parent 99 doesn't exist
			];

			const map = buildChildrenMap(teams);

			expect(map.has(99)).toBe(false);
			expect(map.has(1)).toBe(false);
		});

		it("handles empty input", () => {
			const map = buildChildrenMap([]);
			expect(map.size).toBe(0);
		});
	});

	describe("findRootTeams", () => {
		it("finds teams without parents", () => {
			const teams = [
				createTeam(1, undefined, []),
				createTeam(2, 1, []),
				createTeam(3, undefined, []),
			];

			const roots = findRootTeams(teams);

			expect(roots.map((t) => t.id).sort()).toEqual([1, 3]);
		});

		it("treats orphaned parents as roots", () => {
			const teams = [
				createTeam(1, 99, []), // parent 99 doesn't exist
				createTeam(2, 1, []),
			];

			const roots = findRootTeams(teams);

			expect(roots.map((t) => t.id)).toEqual([1]);
		});
	});

	describe("hasCycle", () => {
		it("returns false for valid hierarchy", () => {
			const teams = [createTeam(1, undefined, []), createTeam(2, 1, []), createTeam(3, 2, [])];
			const teamsById = new Map(teams.map((t) => [t.id, t]));

			expect(hasCycle(1, teamsById)).toBe(false);
			expect(hasCycle(2, teamsById)).toBe(false);
			expect(hasCycle(3, teamsById)).toBe(false);
		});

		it("detects self-referencing cycle", () => {
			const teams = [createTeam(1, 1, [])]; // self-reference
			const teamsById = new Map(teams.map((t) => [t.id, t]));

			expect(hasCycle(1, teamsById)).toBe(true);
		});

		it("detects two-node cycle", () => {
			const team1 = createTeam(1, 2, []);
			const team2 = createTeam(2, 1, []);
			const teamsById = new Map([
				[1, team1],
				[2, team2],
			]);

			expect(hasCycle(1, teamsById)).toBe(true);
			expect(hasCycle(2, teamsById)).toBe(true);
		});

		it("detects multi-node cycle", () => {
			const team1 = createTeam(1, 3, []);
			const team2 = createTeam(2, 1, []);
			const team3 = createTeam(3, 2, []);
			const teamsById = new Map([
				[1, team1],
				[2, team2],
				[3, team3],
			]);

			expect(hasCycle(1, teamsById)).toBe(true);
			expect(hasCycle(2, teamsById)).toBe(true);
			expect(hasCycle(3, teamsById)).toBe(true);
		});
	});

	describe("getDescendantIds", () => {
		it("returns all descendants", () => {
			const teams = [
				createTeam(1, undefined, []),
				createTeam(2, 1, []),
				createTeam(3, 1, []),
				createTeam(4, 2, []),
				createTeam(5, 4, []),
			];
			const childrenMap = buildChildrenMap(teams);

			const descendants = getDescendantIds(1, childrenMap);

			expect([...descendants].sort()).toEqual([2, 3, 4, 5]);
		});

		it("returns empty set for leaf nodes", () => {
			const teams = [createTeam(1, undefined, []), createTeam(2, 1, [])];
			const childrenMap = buildChildrenMap(teams);

			const descendants = getDescendantIds(2, childrenMap);

			expect(descendants.size).toBe(0);
		});

		it("handles cycles gracefully", () => {
			// Create a cycle in the children map manually
			const team1 = createTeam(1, undefined, []);
			const team2 = createTeam(2, 1, []);
			const childrenMap = new Map<number, HierarchyTeam[]>();
			childrenMap.set(1, [team2]);
			childrenMap.set(2, [team1]); // cycle

			// Should not throw and should not infinite loop
			const descendants = getDescendantIds(1, childrenMap);
			expect(descendants.has(2)).toBe(true);
		});
	});

	describe("collectRollupMemberIds", () => {
		it("collects direct members only for leaf team", () => {
			const teams = [createTeam(1, undefined, [100, 101])];
			const teamsById = new Map(teams.map((t) => [t.id, t]));
			const childrenMap = buildChildrenMap(teams);

			const members = collectRollupMemberIds(1, teamsById, childrenMap);

			expect([...members].sort()).toEqual([100, 101]);
		});

		it("collects unique members from team and descendants", () => {
			const teams = [
				createTeam(1, undefined, [100, 101]),
				createTeam(2, 1, [102, 103]),
				createTeam(3, 1, [101, 104]), // 101 is duplicate
			];
			const teamsById = new Map(teams.map((t) => [t.id, t]));
			const childrenMap = buildChildrenMap(teams);

			const members = collectRollupMemberIds(1, teamsById, childrenMap);

			// Should have unique members: 100, 101, 102, 103, 104
			expect([...members].sort()).toEqual([100, 101, 102, 103, 104]);
		});

		it("prevents double counting across nested levels", () => {
			const teams = [
				createTeam(1, undefined, [100]),
				createTeam(2, 1, [100, 101]), // 100 in both parent and child
				createTeam(3, 2, [100, 101, 102]), // all duplicates except 102
			];
			const teamsById = new Map(teams.map((t) => [t.id, t]));
			const childrenMap = buildChildrenMap(teams);

			const members = collectRollupMemberIds(1, teamsById, childrenMap);

			expect([...members].sort()).toEqual([100, 101, 102]);
		});

		it("handles cycles without infinite loop", () => {
			const team1 = createTeam(1, 2, [100]);
			const team2 = createTeam(2, 1, [101]);
			const teamsById = new Map([
				[1, team1],
				[2, team2],
			]);
			// Manually create children map with cycle
			const childrenMap = new Map<number, HierarchyTeam[]>();
			childrenMap.set(1, [team2]);
			childrenMap.set(2, [team1]);

			const members = collectRollupMemberIds(1, teamsById, childrenMap);

			// Should collect members without infinite loop
			expect(members.has(100)).toBe(true);
			expect(members.has(101)).toBe(true);
		});
	});

	describe("computeMemberCounts", () => {
		it("computes both direct and rollup counts", () => {
			const teams = [
				createTeam(1, undefined, [100, 101]),
				createTeam(2, 1, [102]),
				createTeam(3, 2, [103, 104]),
			];

			const counts = computeMemberCounts(teams);

			expect(counts.get(1)).toEqual({ direct: 2, rollup: 5 });
			expect(counts.get(2)).toEqual({ direct: 1, rollup: 3 });
			expect(counts.get(3)).toEqual({ direct: 2, rollup: 2 });
		});

		it("handles shared members correctly", () => {
			const teams = [
				createTeam(1, undefined, [100]),
				createTeam(2, 1, [100, 101]), // 100 is shared
			];

			const counts = computeMemberCounts(teams);

			// Rollup for team 1 should be 2 (100, 101) not 3
			expect(counts.get(1)).toEqual({ direct: 1, rollup: 2 });
			expect(counts.get(2)).toEqual({ direct: 2, rollup: 2 });
		});
	});

	describe("getMemberCount", () => {
		it("returns direct count for direct mode", () => {
			const team = createTeam(1, undefined, [100, 101, 102]);

			const count = getMemberCount(team, "direct");

			expect(count).toBe(3);
		});

		it("returns rollup count from precomputed map", () => {
			const team = createTeam(1, undefined, [100]);
			const memberCounts = new Map([[1, { direct: 1, rollup: 5 }]]);

			const count = getMemberCount(team, "rollup", memberCounts);

			expect(count).toBe(5);
		});

		it("computes rollup count if teams provided", () => {
			const teams = [createTeam(1, undefined, [100]), createTeam(2, 1, [101, 102])];

			const count = getMemberCount(teams[0], "rollup", undefined, teams);

			expect(count).toBe(3);
		});

		it("falls back to direct count if no data available", () => {
			const team = createTeam(1, undefined, [100, 101]);

			const count = getMemberCount(team, "rollup");

			expect(count).toBe(2); // Falls back to direct
		});
	});

	describe("validateHierarchy", () => {
		it("returns empty array for valid hierarchy", () => {
			const teams = [createTeam(1, undefined, []), createTeam(2, 1, []), createTeam(3, 2, [])];

			const issues = validateHierarchy(teams);

			expect(issues).toEqual([]);
		});

		it("detects orphaned parent references", () => {
			const teams = [createTeam(1, 99, [])];

			const issues = validateHierarchy(teams);

			expect(issues).toContainEqual({
				teamId: 1,
				issue: "Parent team 99 not found",
			});
		});

		it("detects self-reference", () => {
			const teams = [createTeam(1, 1, [])];

			const issues = validateHierarchy(teams);

			expect(issues).toContainEqual({
				teamId: 1,
				issue: "Team references itself as parent",
			});
		});

		it("detects cycles", () => {
			const teams = [createTeam(1, 2, []), createTeam(2, 1, [])];

			const issues = validateHierarchy(teams);

			// Both teams should report cycle issues
			expect(issues.filter((i) => i.issue.includes("cycle"))).toHaveLength(2);
		});
	});

	describe("real-world scenarios", () => {
		it("handles deep hierarchy (5+ levels)", () => {
			const teams = [
				createTeam(1, undefined, [100]),
				createTeam(2, 1, [101]),
				createTeam(3, 2, [102]),
				createTeam(4, 3, [103]),
				createTeam(5, 4, [104]),
				createTeam(6, 5, [105]),
			];

			const counts = computeMemberCounts(teams);

			expect(counts.get(1)).toEqual({ direct: 1, rollup: 6 });
			expect(counts.get(6)).toEqual({ direct: 1, rollup: 1 });
		});

		it("handles wide hierarchy (many siblings)", () => {
			const teams = [
				createTeam(1, undefined, [100]),
				createTeam(2, 1, [101]),
				createTeam(3, 1, [102]),
				createTeam(4, 1, [103]),
				createTeam(5, 1, [104]),
				createTeam(6, 1, [105]),
			];

			const counts = computeMemberCounts(teams);

			expect(counts.get(1)).toEqual({ direct: 1, rollup: 6 });
		});

		it("handles diamond pattern (shared descendants)", () => {
			// This tests the pattern where a member exists in multiple paths
			const teams = [
				createTeam(1, undefined, [100]),
				createTeam(2, 1, [101]),
				createTeam(3, 1, [102]),
				createTeam(4, 2, [103]), // child of 2
				// Note: true diamond would need multiple parents, which isn't supported
			];

			const counts = computeMemberCounts(teams);

			expect(counts.get(1)).toEqual({ direct: 1, rollup: 4 });
		});
	});
});
