import { describe, expect, it } from "vitest";

import {
	needsALookFirst,
	percentText,
	practiceMarker,
	shareOf,
	withheldClass,
} from "./review-attention";

describe("shareOf and percentText", () => {
	it("says nothing of nothing is nothing", () => {
		expect(shareOf(3, 0)).toBe(0);
		expect(percentText(0)).toBe("0 %");
	});

	it("keeps a small share visible", () => {
		expect(percentText(0.0004)).toBe("0.1 %");
		expect(percentText(0.004)).toBe("0.4 %");
		expect(percentText(0.304)).toBe("30 %");
	});
});

describe("withheld classes", () => {
	it("counts only the work moving on before the comment was ready as timing", () => {
		for (const reason of [
			"ARTIFACT_CLOSED",
			"ARTIFACT_MERGED",
			"REVIEWED_REVISION_CHANGED",
			"STALE_ROLLOUT_REVISION",
		] as const) {
			expect(withheldClass(reason)).toBe("TIMING");
		}
		expect(withheldClass("COMPOSER_WITHHELD")).toBe("HELD_BY_REVIEW");
		expect(withheldClass("RECIPIENT_OPTED_OUT")).toBe("DEVELOPER_CHOICE");
		expect(withheldClass("OBSERVATION_INVALIDATED")).toBe("OTHER");
	});
});

const observed = (met: number, notMet: number, notApplicable: number, undetermined: number) => ({
	met,
	notMet,
	notApplicable,
	undetermined,
});

describe("practiceMarker", () => {
	it("marks a practice that never applies from 90 % not applicable", () => {
		expect(practiceMarker(observed(1, 1, 18, 0))).toStrictEqual({
			marker: "NEVER_APPLIES",
			part: 18,
			whole: 20,
		});
		expect(practiceMarker(observed(2, 1, 17, 0))).toBeUndefined();
	});

	it("marks a practice that is often undecided from 30 % undetermined", () => {
		expect(practiceMarker(observed(10, 4, 0, 6))?.marker).toBe("OFTEN_UNDECIDED");
		expect(practiceMarker(observed(11, 4, 0, 5))).toBeUndefined();
	});

	it("marks nothing below 20 observations", () => {
		expect(practiceMarker(observed(0, 0, 19, 0))).toBeUndefined();
	});
});

const row = (name: string, observations: ReturnType<typeof observed>, invalidated = 0) => ({
	name,
	observations,
	observationsInvalidated: invalidated,
});

describe("needsALookFirst", () => {
	it("puts the marked practices first, the stronger marker ahead", () => {
		const rows = [
			row("busiest", observed(30, 10, 2, 1)),
			row("undecided", observed(10, 4, 0, 10)),
			row("quiet", observed(3, 1, 0, 0)),
			row("never", observed(0, 0, 20, 0)),
		];
		expect(needsALookFirst(rows).map(({ name }) => name)).toStrictEqual([
			"never",
			"undecided",
			"busiest",
			"quiet",
		]);
	});

	it("orders the unmarked by marked incorrect, then undetermined, then as they came", () => {
		const rows = [
			row("busiest", observed(30, 10, 2, 0)),
			row("one undetermined", observed(10, 4, 0, 1)),
			row("two undetermined", observed(8, 4, 0, 2)),
			row("marked incorrect", observed(3, 1, 0, 0), 1),
			row("quiet", observed(3, 1, 0, 0)),
		];
		expect(needsALookFirst(rows).map(({ name }) => name)).toStrictEqual([
			"marked incorrect",
			"two undetermined",
			"one undetermined",
			"busiest",
			"quiet",
		]);
	});

	it("keeps the order it was given when nothing needs a look", () => {
		const rows = [row("a", observed(5, 0, 0, 0)), row("b", observed(3, 1, 0, 0))];
		expect(needsALookFirst(rows).map(({ name }) => name)).toStrictEqual(["a", "b"]);
	});
});
