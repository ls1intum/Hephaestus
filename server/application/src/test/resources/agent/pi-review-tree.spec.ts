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
	const laneCases: Array<{
		name: string;
		practices: ReviewPractice[];
		expected: Array<[string, string[]]>;
	}> = [
		{
			name: "routes pull request comments to the review lane",
			practices: [
				{
					slug: "specific-review-comments",
					readsSources: ["scm.pull-request.core", "scm.pull-request.comments"],
				},
			],
			expected: [["review-1", ["specific-review-comments"]]],
		},
		{
			name: "routes review threads to the review lane",
			practices: [
				{
					slug: "engages-with-review-feedback",
					readsSources: ["scm.pull-request.core", "scm.review-threads"],
				},
			],
			expected: [["review-1", ["engages-with-review-feedback"]]],
		},
		{
			name: "routes diffs and repository trees to the code lane",
			practices: [
				{
					slug: "tests-behavior-changes",
					readsSources: ["scm.pull-request.diff", "scm.repository.tree"],
				},
			],
			expected: [["code-1", ["tests-behavior-changes"]]],
		},
		{
			name: "routes linked work to the linked-work lane",
			practices: [
				{
					slug: "meets-linked-acceptance-criteria",
					readsSources: ["scm.pull-request.core", "scm.linked-work-items"],
				},
			],
			expected: [["linked-work-1", ["meets-linked-acceptance-criteria"]]],
		},
		{
			name: "routes pull request core data to the pull-request lane",
			practices: [
				{ slug: "clear-pull-request-description", readsSources: ["scm.pull-request.core"] },
			],
			expected: [["pull-request-1", ["clear-pull-request-description"]]],
		},
	];

	for (const scenario of laneCases) {
		test(scenario.name, () => {
			const tree = buildReviewTree(scenario.practices, 4);

			expect(tree.practiceCount).toBe(scenario.practices.length);
			expect(tree.groups.map((group) => [group.id, group.practiceSlugs])).toEqual(
				scenario.expected,
			);
		});
	}

	test("classifies mixed evidence lanes deterministically", () => {
		const mixed: ReviewPractice[] = laneCases.flatMap((scenario) => scenario.practices);
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

	test("returns no work for an empty catalogue", () => {
		expect(buildReviewTree([], 4)).toEqual({ practiceCount: 0, groups: [] });
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

	test("keeps groups separate when area names normalize alike", () => {
		const tree = buildReviewTree(
			[
				{ slug: "api", area: "API / UX", readsSources: ["scm.pull-request.diff"] },
				{ slug: "ux", area: "API---UX", readsSources: ["scm.pull-request.diff"] },
			],
			2,
		);

		expect(tree.groups.map((group) => group.id)).toEqual(["code-1", "code-2"]);
	});

	test("normalizes sources before classifying a group", () => {
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
		});
	});

	test("rejects invalid capacity, blank slugs, and duplicate slugs", () => {
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

test("derives bounded review concurrency", () => {
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
	const started: number[] = [];
	const releases = Array.from({ length: 4 }, () => Promise.withResolvers<undefined>());
	const firstPairStarted = Promise.withResolvers<undefined>();
	const thirdStarted = Promise.withResolvers<undefined>();
	const resultPromise = mapConcurrent([0, 1, 2, 3], 2, async (_, index) => {
		started.push(index);
		if (started.length === 2) firstPairStarted.resolve(undefined);
		if (started.length === 3) thirdStarted.resolve(undefined);
		await releases[index]?.promise;
		return index;
	});

	await firstPairStarted.promise;
	expect(started).toEqual([0, 1]);
	releases[1]?.resolve(undefined);
	await thirdStarted.promise;
	expect(started).toEqual([0, 1, 2]);
	for (const release of releases) release.resolve(undefined);
	const results = await resultPromise;
	expect(results).toEqual([0, 1, 2, 3]);
});
