import { describe, expect, it } from "vitest";

import { ACROSS_WORKSPACE, outcomes } from "@/stories/practices-across-the-workspace-story-data";

import { groupsOf, outcomeTotal } from "./workspace-groups";

describe("groupsOf", () => {
	it("keeps the server's order of groups and practices and leaves out a practice in no group", () => {
		const groups = groupsOf(ACROSS_WORKSPACE.practices);
		expect(groups.map((group) => [group.slug, group.practices.length])).toStrictEqual([
			["acting-on-review-feedback", 2],
			["communication", 1],
			["review-ready-work", 5],
			["testing-discipline", 1],
		]);
		expect(groups.flatMap((group) => group.practices).map((one) => one.practiceSlug)).not.toContain(
			"sign-commits",
		);
	});

	it("joins a group's practices even when another group comes between them", () => {
		const practices = [0, 2, 1].flatMap((index) =>
			ACROSS_WORKSPACE.practices.slice(index, index + 1),
		);
		expect(
			groupsOf(practices).map((group) => group.practices.map((one) => one.practiceSlug)),
		).toStrictEqual([["answer-every-comment", "re-request-review"], ["write-in-the-open"]]);
	});
});

describe("outcomeTotal", () => {
	it("adds every outcome across practices", () => {
		expect(outcomeTotal([outcomes([1, 2, 3, 4]), outcomes([10, 20, 30, 40])])).toStrictEqual(
			outcomes([11, 22, 33, 44]),
		);
	});

	it("counts nothing for no practice", () => {
		expect(outcomeTotal([])).toStrictEqual(outcomes([0, 0, 0, 0]));
	});
});
