import { assert, describe, expect, it } from "vitest";
import {
	autonomyDistribution,
	autonomyDistributionSentence,
	autonomyTotal,
} from "@/lib/practice-autonomy";
import {
	countOverrides,
	groupPracticesByArea,
	isOverridden,
	reviewableByHephaestus,
	UNASSIGNED_AREA_KEY,
} from "./practice-autonomy-model";
import { buildAutonomyFixture, scaleFixture } from "./story-mock-data";

const fixture = buildAutonomyFixture({
	workspaceDefault: "HUMAN_APPROVAL",
	areas: [
		{
			slug: "hygiene",
			name: "Hygiene",
			practices: [{ name: "One" }, { name: "Two", override: "AUTOMATIC" }],
		},
		{ slug: "testing", name: "Testing", override: "OFF", practices: [{ name: "Three" }] },
		{ slug: null, name: null, practices: [{ name: "Four" }] },
	],
});

describe("groupPracticesByArea", () => {
	it("keeps the server's area order and its counts", () => {
		const groups = groupPracticesByArea(fixture.rollup, fixture.practices);

		expect(groups.map((group) => group.name)).toStrictEqual([
			"Hygiene",
			"Testing",
			"Not in an area",
		]);
		const [hygiene] = groups;
		const [hygieneRollup] = fixture.rollup.areas;
		assert(hygiene);
		assert(hygieneRollup);
		expect(hygiene.counts).toStrictEqual(hygieneRollup.counts);
	});

	it("keys the no-area group so it can be rendered, and leaves it without an area slug", () => {
		const groups = groupPracticesByArea(fixture.rollup, fixture.practices);
		const unassigned = groups.at(-1);

		expect(unassigned?.key).toBe(UNASSIGNED_AREA_KEY);
		expect(unassigned?.areaSlug).toBeNull();
		expect(unassigned?.practices.map((practice) => practice.name)).toStrictEqual(["Four"]);
	});

	it("keeps an area the rollup lists with no practices, so its own autonomy stays reachable", () => {
		const empty = buildAutonomyFixture({
			areas: [{ slug: "quiet", name: "Quiet", override: "OFF", practices: [] }],
		});

		const groups = groupPracticesByArea(empty.rollup, empty.practices);

		expect(groups).toHaveLength(1);
		const [quiet] = groups;
		assert(quiet);
		expect(quiet.practices).toStrictEqual([]);
		expect(quiet.areaSlug).toBe("quiet");
	});

	it("does not drop a practice whose area the rollup has not caught up with", () => {
		const stale = { ...fixture.rollup, areas: fixture.rollup.areas.slice(1) };

		const groups = groupPracticesByArea(stale, fixture.practices);

		expect(groups.at(-1)?.practices.map((practice) => practice.name)).toStrictEqual(["One", "Two"]);
	});
});

describe("the overrides-only filter", () => {
	it("keeps only the practices somebody set by hand", () => {
		const groups = groupPracticesByArea(fixture.rollup, fixture.practices, {
			overridesOnly: true,
		});

		expect(
			groups.flatMap((group) => group.practices).map((practice) => practice.name),
		).toStrictEqual(["Two"]);
	});

	it("keeps an area that decided for itself even when none of its practices did", () => {
		const groups = groupPracticesByArea(fixture.rollup, fixture.practices, {
			overridesOnly: true,
		});

		const testing = groups.find((group) => group.areaSlug === "testing");
		expect(testing).toBeDefined();
		expect(testing?.practices).toStrictEqual([]);
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
	it("reads the override, not the level that decided the autonomy", () => {
		// An area that chose its own autonomy reports source AREA and inherited false; deriving it from
		// `source` instead would call it inherited and hide it from the filter above.
		const [hygiene, testing] = fixture.rollup.areas;
		assert(hygiene);
		assert(testing);

		expect(testing.autonomy.source).toBe("AREA");
		expect(isOverridden(testing.autonomy)).toBe(true);
		expect(isOverridden(hygiene.autonomy)).toBe(false);
	});
});

describe("countOverrides", () => {
	it("counts both levels, because an admin who only set area autonomies has still decided something", () => {
		expect(countOverrides(fixture.rollup)).toStrictEqual({ practices: 1, areas: 1 });
	});
});

describe("reviewableByHephaestus", () => {
	it("refuses the modes the server refuses, so the control is never offered a choice that fails", () => {
		const unreviewable = buildAutonomyFixture({
			areas: [{ slug: "a", name: "A", practices: [{ name: "Manual", reviewable: false }] }],
		});

		const [manual] = unreviewable.practices;
		const [reviewable] = fixture.practices;
		assert(manual);
		assert(reviewable);
		expect(reviewableByHephaestus(manual.automatedReviewPolicy)).toBe(false);
		expect(reviewableByHephaestus(reviewable.automatedReviewPolicy)).toBe(true);
	});
});

describe("the workspace summary", () => {
	it("drops the autonomies the rollup sends at zero, and keeps ladder order", () => {
		// The rollup sends every autonomy as a key even at zero, so a caller never has to gap-fill.
		expect(
			autonomyDistribution({ OFF: 3, HUMAN_APPROVAL: 0, AUTOMATIC: 1 }).map(
				({ autonomy }) => autonomy,
			),
		).toStrictEqual(["OFF", "AUTOMATIC"]);
		expect(
			autonomyDistribution(fixture.rollup.counts).map(({ autonomy }) => autonomy),
		).toStrictEqual(["OFF", "HUMAN_APPROVAL", "AUTOMATIC"]);
	});

	it("reads as a sentence for the live region, not as middot-separated fragments", () => {
		expect(autonomyDistributionSentence(fixture.rollup.counts)).toBe(
			"4 practices: 1 off, 2 review before sending and 1 send automatically.",
		);
	});

	it("says something rather than nothing for a workspace with no practices", () => {
		expect(autonomyDistributionSentence({})).toBe("No practices yet.");
		expect(autonomyTotal({})).toBe(0);
	});

	it("answers for a hundred practices from the rollup alone", () => {
		const scale = scaleFixture();

		expect(autonomyTotal(scale.rollup.counts)).toBe(100);
		expect(scale.rollup.areas).toHaveLength(25);
		expect(autonomyDistributionSentence(scale.rollup.counts)).toBe(
			"100 practices: 6 off, 89 review before sending and 5 send automatically.",
		);
	});
});
