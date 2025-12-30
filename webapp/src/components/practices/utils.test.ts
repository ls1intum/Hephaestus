/**
 * Tests for practices utility functions.
 * Verifies state classification logic for bad practices.
 */
import { describe, expect, it } from "vitest";
import type { PullRequestBadPractice } from "@/api/types.gen";
import { filterGoodAndBadPractices, isResolvedState, isUnresolvedIssue } from "./utils";

describe("isUnresolvedIssue", () => {
	it("returns true for CRITICAL_ISSUE", () => {
		expect(isUnresolvedIssue("CRITICAL_ISSUE")).toBe(true);
	});

	it("returns true for NORMAL_ISSUE", () => {
		expect(isUnresolvedIssue("NORMAL_ISSUE")).toBe(true);
	});

	it("returns true for MINOR_ISSUE", () => {
		expect(isUnresolvedIssue("MINOR_ISSUE")).toBe(true);
	});

	it("returns false for FIXED", () => {
		expect(isUnresolvedIssue("FIXED")).toBe(false);
	});

	it("returns false for WONT_FIX", () => {
		expect(isUnresolvedIssue("WONT_FIX")).toBe(false);
	});

	it("returns false for WRONG", () => {
		expect(isUnresolvedIssue("WRONG")).toBe(false);
	});

	it("returns false for GOOD_PRACTICE", () => {
		expect(isUnresolvedIssue("GOOD_PRACTICE")).toBe(false);
	});
});

describe("isResolvedState", () => {
	it("returns true for FIXED", () => {
		expect(isResolvedState("FIXED")).toBe(true);
	});

	it("returns true for WONT_FIX", () => {
		expect(isResolvedState("WONT_FIX")).toBe(true);
	});

	it("returns true for WRONG", () => {
		expect(isResolvedState("WRONG")).toBe(true);
	});

	it("returns false for CRITICAL_ISSUE", () => {
		expect(isResolvedState("CRITICAL_ISSUE")).toBe(false);
	});

	it("returns false for NORMAL_ISSUE", () => {
		expect(isResolvedState("NORMAL_ISSUE")).toBe(false);
	});

	it("returns false for MINOR_ISSUE", () => {
		expect(isResolvedState("MINOR_ISSUE")).toBe(false);
	});

	it("returns false for GOOD_PRACTICE", () => {
		expect(isResolvedState("GOOD_PRACTICE")).toBe(false);
	});
});

describe("filterGoodAndBadPractices", () => {
	const createPractice = (
		id: number,
		state: PullRequestBadPractice["state"],
	): PullRequestBadPractice => ({
		id,
		title: `Practice ${id}`,
		description: `Description ${id}`,
		state,
	});

	it("correctly categorizes good practices", () => {
		const practices = [createPractice(1, "GOOD_PRACTICE"), createPractice(2, "CRITICAL_ISSUE")];

		const result = filterGoodAndBadPractices(practices);

		expect(result.goodPractices).toHaveLength(1);
		expect(result.goodPractices[0].id).toBe(1);
	});

	it("correctly categorizes bad practices (issues)", () => {
		const practices = [
			createPractice(1, "CRITICAL_ISSUE"),
			createPractice(2, "NORMAL_ISSUE"),
			createPractice(3, "MINOR_ISSUE"),
			createPractice(4, "GOOD_PRACTICE"),
		];

		const result = filterGoodAndBadPractices(practices);

		expect(result.badPractices).toHaveLength(3);
		expect(result.badPractices.map((p) => p.id)).toEqual([1, 2, 3]);
	});

	it("correctly categorizes resolved practices", () => {
		const practices = [
			createPractice(1, "FIXED"),
			createPractice(2, "WONT_FIX"),
			createPractice(3, "WRONG"),
			createPractice(4, "NORMAL_ISSUE"),
		];

		const result = filterGoodAndBadPractices(practices);

		expect(result.resolvedPractices).toHaveLength(3);
		expect(result.resolvedPractices.map((p) => p.id)).toEqual([1, 2, 3]);
	});

	it("handles empty array", () => {
		const result = filterGoodAndBadPractices([]);

		expect(result.goodPractices).toHaveLength(0);
		expect(result.badPractices).toHaveLength(0);
		expect(result.resolvedPractices).toHaveLength(0);
	});

	it("correctly handles mixed practices", () => {
		const practices = [
			createPractice(1, "GOOD_PRACTICE"),
			createPractice(2, "CRITICAL_ISSUE"),
			createPractice(3, "FIXED"),
			createPractice(4, "NORMAL_ISSUE"),
			createPractice(5, "WONT_FIX"),
			createPractice(6, "MINOR_ISSUE"),
			createPractice(7, "WRONG"),
		];

		const result = filterGoodAndBadPractices(practices);

		expect(result.goodPractices).toHaveLength(1);
		expect(result.badPractices).toHaveLength(3);
		expect(result.resolvedPractices).toHaveLength(3);
	});
});
