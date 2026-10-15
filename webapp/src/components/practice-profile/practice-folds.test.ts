import { describe, expect, it } from "vitest";

import type { PracticeStanding } from "@/api/types.gen";
import { practiceStandingOf } from "@/stories/practice-profile-story-mock-data";

import { groupNeverApplied, groupNotReviewedYet, splitFoldedPractices } from "./practice-folds";

const standing = (
	slug: string,
	value: PracticeStanding["standing"],
	doesNotApplyOnly = false,
): PracticeStanding => practiceStandingOf({ slug, name: slug, standing: value, doesNotApplyOnly });

describe("splitFoldedPractices", () => {
	it("folds the practices that never applied and the ones not reviewed yet, apart", () => {
		const { shown, neverApplied, notReviewed } = splitFoldedPractices([
			standing("a", "NO_OPPORTUNITY", true),
			standing("b", "NO_OPPORTUNITY", false),
			standing("c", "NOT_OBSERVED"),
			standing("d", "DEVELOPING"),
		]);
		expect(neverApplied.map((practice) => practice.slug)).toStrictEqual(["a"]);
		expect(notReviewed.map((practice) => practice.slug)).toStrictEqual(["c"]);
		expect(shown.map((practice) => practice.slug)).toStrictEqual(["b", "d"]);
	});

	it("folds a group only when every practice in it never applied", () => {
		expect(groupNeverApplied([standing("a", "NO_OPPORTUNITY", true)])).toBe(true);
		expect(groupNeverApplied([standing("a", "NO_OPPORTUNITY", true), standing("b", "MIXED")])).toBe(
			false,
		);
		expect(groupNeverApplied([])).toBe(false);
	});

	it("folds a group as not reviewed only when no practice in it was reviewed", () => {
		expect(
			groupNotReviewedYet([standing("a", "NOT_OBSERVED"), standing("b", "NOT_OBSERVED")]),
		).toBe(true);
		expect(groupNotReviewedYet([standing("a", "NOT_OBSERVED"), standing("b", "MIXED")])).toBe(
			false,
		);
		expect(groupNotReviewedYet([])).toBe(false);
	});

	it("folds a group that mixes never applied and not reviewed as not reviewed", () => {
		const mixed = [standing("a", "NO_OPPORTUNITY", true), standing("b", "NOT_OBSERVED")];
		expect(groupNotReviewedYet(mixed)).toBe(true);
		expect(groupNeverApplied(mixed)).toBe(false);
		// A group whose every practice never applied keeps its own fold.
		expect(groupNotReviewedYet([standing("a", "NO_OPPORTUNITY", true)])).toBe(false);
		expect(
			groupNotReviewedYet([standing("a", "NO_OPPORTUNITY"), standing("b", "NOT_OBSERVED")]),
		).toBe(false);
	});
});
