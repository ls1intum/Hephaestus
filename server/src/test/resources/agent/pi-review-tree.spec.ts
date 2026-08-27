import { describe, expect, test } from "bun:test";
import {
	buildReviewTree,
	mapConcurrent,
	missingPracticeSlugs,
	type ReviewPractice,
	resolveReviewConcurrency,
} from "../../../main/resources/agent/pi-review-tree";

const practices: ReviewPractice[] = [
	{
		slug: "unsafe-input",
		readsSources: ["scm.repository.tree", "scm.pull-request.diff"],
	},
	{ slug: "description", readsSources: ["scm.pull-request.core"] },
	{
		slug: "review-replies",
		readsSources: ["scm.pull-request.core", "scm.review-threads"],
	},
	{
		slug: "acceptance-criteria",
		readsSources: ["scm.pull-request.core", "scm.linked-work-items"],
	},
];

describe("buildReviewTree", () => {
	const scenarioContracts: Array<{
		name: string;
		practices: ReviewPractice[];
		expected: Array<[string, string[], string[]]>;
	}> = [
		{
			name: "a submitted-review occasion reads review comments without fanning out to code",
			practices: [
				{
					slug: "specific-review-comments",
					readsSources: ["scm.pull-request.core", "scm.pull-request.comments"],
				},
			],
			expected: [
				[
					"review-1",
					["specific-review-comments"],
					["scm.pull-request.comments", "scm.pull-request.core"],
				],
			],
		},
		{
			name: "an author-uptake occasion reads review threads without fanning out to code",
			practices: [
				{
					slug: "engages-with-review-feedback",
					readsSources: ["scm.pull-request.core", "scm.review-threads"],
				},
			],
			expected: [
				[
					"review-1",
					["engages-with-review-feedback"],
					["scm.pull-request.core", "scm.review-threads"],
				],
			],
		},
		{
			name: "a change occasion keeps code exploration local to code practices",
			practices: [
				{
					slug: "tests-behavior-changes",
					readsSources: ["scm.pull-request.diff", "scm.repository.tree"],
				},
			],
			expected: [
				["code-1", ["tests-behavior-changes"], ["scm.pull-request.diff", "scm.repository.tree"]],
			],
		},
		{
			name: "a linked-work occasion does not require diff exploration",
			practices: [
				{
					slug: "meets-linked-acceptance-criteria",
					readsSources: ["scm.pull-request.core", "scm.linked-work-items"],
				},
			],
			expected: [
				[
					"linked-work-1",
					["meets-linked-acceptance-criteria"],
					["scm.linked-work-items", "scm.pull-request.core"],
				],
			],
		},
		{
			name: "a core-only occasion stays in the pull-request lane",
			practices: [
				{ slug: "clear-pull-request-description", readsSources: ["scm.pull-request.core"] },
			],
			expected: [["pull-request-1", ["clear-pull-request-description"], ["scm.pull-request.core"]]],
		},
	];

	for (const scenario of scenarioContracts) {
		test(scenario.name, () => {
			const tree = buildReviewTree(scenario.practices, 4);

			expect(tree.practiceCount).toBe(scenario.practices.length);
			expect(
				tree.groups.map((group) => [group.id, group.practiceSlugs, group.evidenceSources]),
			).toEqual(scenario.expected);
		});
	}

	test("keeps mixed-scenario evidence lanes deterministic and isolated", () => {
		const mixed: ReviewPractice[] = scenarioContracts.flatMap((scenario) => scenario.practices);
		const expected = [
			["pull-request-1", ["clear-pull-request-description"]],
			["linked-work-1", ["meets-linked-acceptance-criteria"]],
			["review-1", ["engages-with-review-feedback", "specific-review-comments"]],
			["code-1", ["tests-behavior-changes"]],
		];

		expect(
			buildReviewTree(mixed, 4).groups.map((group) => [group.id, group.practiceSlugs]),
		).toEqual(expected);
		expect(
			buildReviewTree(mixed.toReversed(), 4).groups.map((group) => [group.id, group.practiceSlugs]),
		).toEqual(expected);
	});

	test("returns no work for an occasion with no eligible practices", () => {
		expect(buildReviewTree([], 4)).toEqual({ practiceCount: 0, groups: [] });
	});

	test("orders evidence-local groups from cheap context to code exploration", () => {
		const tree = buildReviewTree(practices, 2);

		expect(tree.practiceCount).toBe(4);
		expect(tree.groups.map((group) => [group.id, group.practiceSlugs])).toEqual([
			["pull-request-1", ["description"]],
			["linked-work-1", ["acceptance-criteria"]],
			["review-1", ["review-replies"]],
			["code-1", ["unsafe-input"]],
		]);
	});

	test("uses the configured capacity instead of assuming a catalog size", () => {
		const dynamic = Array.from({ length: 7 }, (_, index) => ({
			slug: `practice-${index}`,
			readsSources: ["scm.pull-request.diff"],
		}));

		const tree = buildReviewTree(dynamic, 3);

		expect(tree.practiceCount).toBe(7);
		expect(tree.groups.map((group) => group.practiceSlugs.length)).toEqual([3, 3, 1]);
		expect(tree.groups.map((group) => group.id)).toEqual(["code-1", "code-2", "code-3"]);
	});

	test("is deterministic when the practice index order changes", () => {
		const forward = buildReviewTree(practices, 2);
		const reverse = buildReviewTree(practices.toReversed(), 2);

		expect(reverse).toEqual(forward);
	});

	test("keeps group ids unique when area names normalize alike", () => {
		const tree = buildReviewTree(
			[
				{ slug: "api", area: "API / UX", readsSources: ["scm.pull-request.diff"] },
				{ slug: "ux", area: "API---UX", readsSources: ["scm.pull-request.diff"] },
			],
			1,
		);

		expect(tree.groups.map((group) => group.id)).toEqual(["code-1", "code-2"]);
		expect(new Set(tree.groups.map((group) => group.id)).size).toBe(tree.groups.length);
	});

	test("normalizes and combines the sources required by a group", () => {
		const tree = buildReviewTree(
			[
				{ slug: "b", readsSources: [" scm.pull-request.core "] },
				{
					slug: "a",
					readsSources: ["scm.pull-request.core", "scm.pull-request.core"],
				},
			],
			2,
		);

		expect(tree.groups[0]).toEqual({
			id: "pull-request-1",
			lane: "pull-request",
			practiceSlugs: ["a", "b"],
			evidenceSources: ["scm.pull-request.core"],
		});
	});

	test("rejects invalid capacity and ambiguous practice indexes", () => {
		expect(() => buildReviewTree(practices, 0)).toThrow("positive integer");
		expect(() => buildReviewTree([{ slug: " " }], 1)).toThrow("non-empty slug");
		expect(() => buildReviewTree([{ slug: "same" }, { slug: "same" }], 1)).toThrow(
			"duplicate practice slug: same",
		);
	});
});

test("missingPracticeSlugs requires explicit coverage for every selected practice", () => {
	expect(missingPracticeSlugs(["a", "b", "c"], ["c", "a"])).toEqual(["b"]);
	expect(missingPracticeSlugs(["a", "b"], ["b", "a"])).toEqual([]);
});

test("review concurrency scales with the catalogue without overloading the provider", () => {
	expect(resolveReviewConcurrency(undefined, 1)).toBe(1);
	expect(resolveReviewConcurrency(undefined, 16)).toBe(4);
	expect(resolveReviewConcurrency(undefined, 27)).toBe(7);
	expect(resolveReviewConcurrency(undefined, 100)).toBe(8);
	expect(resolveReviewConcurrency("1", 27)).toBe(1);
	expect(resolveReviewConcurrency("8", 1)).toBe(8);
	for (const invalid of ["0", "9", "2.5", "nope"]) {
		expect(() => resolveReviewConcurrency(invalid, 27)).toThrow("integer from 1 to 8");
	}
	expect(() => resolveReviewConcurrency(undefined, -1)).toThrow("non-negative integer");
});

test("mapConcurrent bounds active work and preserves input order", async () => {
	let active = 0;
	let maximum = 0;
	const results = await mapConcurrent([40, 5, 20, 10], 2, async (delay, index) => {
		active++;
		maximum = Math.max(maximum, active);
		await Bun.sleep(delay);
		active--;
		return index;
	});

	expect(maximum).toBe(2);
	expect(results).toEqual([0, 1, 2, 3]);
});
