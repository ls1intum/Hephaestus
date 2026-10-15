import { assert, describe, expect, it } from "vitest";

import type { Practice } from "@/api/types.gen";

import { buildFeedbackModeFixture } from "./fixtures";
import {
	countOverrides,
	groupPracticesByGroup,
	isOverridden,
	practiceName,
	reviewedByHephaestus,
	UNASSIGNED_GROUP_KEY,
} from "./practice-feedback-mode-model";

const fixture = buildFeedbackModeFixture({
	workspaceDefault: "APPROVE_FIRST",
	groups: [
		{
			slug: "hygiene",
			name: "Hygiene",
			practices: [{ name: "One" }, { name: "Two", override: "AUTOMATIC" }],
		},
		{ slug: "testing", name: "Testing", override: "OFF", practices: [{ name: "Three" }] },
		{ slug: null, name: null, practices: [{ name: "Four" }] },
	],
});

const names = (practices: readonly Practice[]) =>
	practices.map((practice) => practiceName(practice));

describe("groupPracticesByGroup", () => {
	it("keeps the saved group order and counts each group's effective feedback modes", () => {
		const groups = groupPracticesByGroup(fixture.groups, fixture.practices);

		expect(groups.map((group) => group.name)).toStrictEqual(["Hygiene", "Testing", "Unassigned"]);
		const [hygiene, testing] = groups;
		assert(hygiene);
		assert(testing);
		expect(hygiene.counts).toStrictEqual({ OFF: 0, APPROVE_FIRST: 1, AUTOMATIC: 1 });
		expect(testing.counts).toStrictEqual({ OFF: 1, APPROVE_FIRST: 0, AUTOMATIC: 0 });
	});

	it("keys the no-group bucket so it can be rendered, and leaves it without a group", () => {
		const unassigned = groupPracticesByGroup(fixture.groups, fixture.practices).at(-1);
		assert(unassigned);

		expect(unassigned.key).toBe(UNASSIGNED_GROUP_KEY);
		expect(unassigned.group).toBeNull();
		expect(names(unassigned.practices)).toStrictEqual(["Four"]);
	});

	it("keeps a group with no practices, so its own feedback mode stays reachable", () => {
		const empty = buildFeedbackModeFixture({
			groups: [{ slug: "quiet", name: "Quiet", override: "OFF", practices: [] }],
		});

		const groups = groupPracticesByGroup(empty.groups, empty.practices);

		expect(groups).toHaveLength(1);
		expect(groups[0]?.practices).toStrictEqual([]);
		expect(groups[0]?.group?.slug).toBe("quiet");
	});
});

describe("the overrides-only filter", () => {
	const filtered = groupPracticesByGroup(fixture.groups, fixture.practices, {
		overridesOnly: true,
	});

	it("keeps only the practices somebody set by hand", () => {
		expect(names(filtered.flatMap((group) => group.practices))).toStrictEqual(["Two"]);
	});

	it("keeps a group that decided for itself even when none of its practices did", () => {
		const testing = filtered.find((group) => group.group?.slug === "testing");
		expect(testing?.practices).toStrictEqual([]);
		expect(testing?.totalPractices).toBe(1);
	});

	it("drops the no-group bucket, which cannot hold a feedback mode of its own", () => {
		expect(filtered.some((group) => group.group === null)).toBe(false);
	});
});

describe("isOverridden", () => {
	it("reads the level that chose the feedback mode", () => {
		const [hygiene, testing] = fixture.groups;
		assert(hygiene);
		assert(testing);

		expect(isOverridden(testing.feedbackMode, "GROUP")).toBe(true);
		expect(isOverridden(hygiene.feedbackMode, "GROUP")).toBe(false);
		const [one, two] = fixture.practices;
		assert(one);
		assert(two);
		expect(isOverridden(one.feedbackMode, "PRACTICE")).toBe(false);
		expect(isOverridden(two.feedbackMode, "PRACTICE")).toBe(true);
	});
});

describe("countOverrides", () => {
	it("counts both levels, because an admin who only set group feedback modes still decided something", () => {
		expect(countOverrides(fixture.groups, fixture.practices)).toStrictEqual({
			practices: 1,
			groups: 1,
		});
	});
});

describe("reviewedByHephaestus", () => {
	it("tells reviewed by people or nobody apart from a practice that Hephaestus reviews", () => {
		const others = buildFeedbackModeFixture({
			groups: [
				{
					slug: "a",
					name: "A",
					practices: [
						{ name: "People", reviewedBy: "PEOPLE" },
						{ name: "Nobody", reviewedBy: "NOBODY" },
					],
				},
			],
		});

		expect(others.practices.map(reviewedByHephaestus)).toStrictEqual([false, false]);
		expect(fixture.practices.map(reviewedByHephaestus)).toStrictEqual([true, true, true, true]);
	});
});
