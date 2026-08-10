import { describe, expect, it } from "vitest";
import { tierDistribution, tierDistributionSentence, tierTotal } from "@/lib/review-tiers";
import {
	countOverrides,
	groupPracticesByArea,
	isOverridden,
	reviewableByHephaestus,
	UNASSIGNED_AREA_KEY,
} from "./review-autonomy-model";
import { buildAutonomyFixture, scaleFixture } from "./story-mock-data";

const fixture = buildAutonomyFixture({
	workspaceDefault: "OBSERVE",
	areas: [
		{
			slug: "hygiene",
			name: "Hygiene",
			practices: [{ name: "One" }, { name: "Two", override: "DELIVER" }],
		},
		{ slug: "testing", name: "Testing", override: "OFF", practices: [{ name: "Three" }] },
		{ slug: null, name: null, practices: [{ name: "Four" }] },
	],
});

describe("groupPracticesByArea", () => {
	it("keeps the server's area order and its counts", () => {
		const groups = groupPracticesByArea(fixture.rollup, fixture.practices);

		expect(groups.map((group) => group.name)).toEqual(["Hygiene", "Testing", "Not in an area"]);
		expect(groups[0].counts).toEqual(fixture.rollup.areas[0].counts);
	});

	it("keys the no-area group so it can be rendered, and leaves it without an area slug", () => {
		const groups = groupPracticesByArea(fixture.rollup, fixture.practices);
		const unassigned = groups.at(-1);

		expect(unassigned?.key).toBe(UNASSIGNED_AREA_KEY);
		expect(unassigned?.areaSlug).toBeNull();
		expect(unassigned?.practices.map((practice) => practice.name)).toEqual(["Four"]);
	});

	it("keeps an area the rollup lists with no practices, so its own tier stays reachable", () => {
		const empty = buildAutonomyFixture({
			areas: [{ slug: "quiet", name: "Quiet", override: "OFF", practices: [] }],
		});

		const groups = groupPracticesByArea(empty.rollup, empty.practices);

		expect(groups).toHaveLength(1);
		expect(groups[0].practices).toEqual([]);
		expect(groups[0].areaSlug).toBe("quiet");
	});

	it("does not drop a practice whose area the rollup has not caught up with", () => {
		const stale = { ...fixture.rollup, areas: fixture.rollup.areas.slice(1) };

		const groups = groupPracticesByArea(stale, fixture.practices);

		expect(groups.at(-1)?.practices.map((practice) => practice.name)).toEqual(["One", "Two"]);
	});
});

describe("the overrides-only filter", () => {
	it("keeps only the practices somebody set by hand", () => {
		const groups = groupPracticesByArea(fixture.rollup, fixture.practices, {
			overridesOnly: true,
		});

		expect(groups.flatMap((group) => group.practices).map((practice) => practice.name)).toEqual([
			"Two",
		]);
	});

	it("keeps an area that decided for itself even when none of its practices did", () => {
		const groups = groupPracticesByArea(fixture.rollup, fixture.practices, {
			overridesOnly: true,
		});

		const testing = groups.find((group) => group.areaSlug === "testing");
		expect(testing).toBeDefined();
		expect(testing?.practices).toEqual([]);
		// It still knows how many rows it is hiding, so the group can say so rather than look broken.
		expect(testing?.totalPractices).toBe(1);
	});

	it("drops the no-area group, which cannot hold a decision of its own", () => {
		const groups = groupPracticesByArea(fixture.rollup, fixture.practices, {
			overridesOnly: true,
		});

		expect(groups.some((group) => group.areaSlug === null)).toBe(false);
	});
});

describe("isOverridden", () => {
	it("reads the override, not the level that decided the tier", () => {
		// An area that chose its own tier reports source AREA and inherited false. Deriving this from
		// `source !== "PRACTICE"` would call it inherited and hide it from the filter above.
		const testing = fixture.rollup.areas[1].reviewTier;

		expect(testing.source).toBe("AREA");
		expect(isOverridden(testing)).toBe(true);
		expect(isOverridden(fixture.rollup.areas[0].reviewTier)).toBe(false);
	});
});

describe("countOverrides", () => {
	it("counts both levels, because an admin who only set area tiers has still decided something", () => {
		expect(countOverrides(fixture.rollup)).toEqual({ practices: 1, areas: 1 });
	});

	it("still answers for the whole workspace while the list is filtered down to a handful", () => {
		const filtered = groupPracticesByArea(fixture.rollup, fixture.practices, {
			overridesOnly: true,
		});

		expect(filtered.flatMap((group) => group.practices)).toHaveLength(1);
		expect(countOverrides(fixture.rollup).practices).toBe(1);
	});
});

describe("reviewableByHephaestus", () => {
	it("refuses the modes the server refuses, so the control is never offered a choice that fails", () => {
		const unreviewable = buildAutonomyFixture({
			areas: [{ slug: "a", name: "A", practices: [{ name: "Manual", reviewable: false }] }],
		});

		expect(reviewableByHephaestus(unreviewable.practices[0].automatedReviewPolicy)).toBe(false);
		expect(reviewableByHephaestus(fixture.practices[0].automatedReviewPolicy)).toBe(true);
	});
});

describe("the workspace summary", () => {
	it("drops the empty tiers the rollup always sends, including the one nobody can select", () => {
		expect(fixture.rollup.counts.PROPOSE).toBe(0);
		expect(tierDistribution(fixture.rollup.counts).map(({ tier }) => tier)).toEqual([
			"OFF",
			"OBSERVE",
			"DELIVER",
		]);
	});

	it("reads as a sentence for the live region, not as middot-separated fragments", () => {
		expect(tierDistributionSentence(fixture.rollup.counts)).toBe(
			"4 practices: 1 off, 2 observe and 1 deliver.",
		);
	});

	it("says something rather than nothing for a workspace with no practices", () => {
		expect(tierDistributionSentence({})).toBe("No practices yet.");
		expect(tierTotal({})).toBe(0);
	});

	it("answers for a hundred practices from the rollup alone", () => {
		const scale = scaleFixture();

		expect(tierTotal(scale.rollup.counts)).toBe(100);
		expect(scale.rollup.areas).toHaveLength(25);
		expect(tierDistributionSentence(scale.rollup.counts)).toBe(
			"100 practices: 6 off, 89 observe and 5 deliver.",
		);
	});
});
