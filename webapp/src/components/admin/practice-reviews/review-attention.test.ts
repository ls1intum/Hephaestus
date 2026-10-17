import { describe, expect, it } from "vitest";

import {
	needsALookFirst,
	percentText,
	PRACTICE_MARKER_DEFS,
	practiceMarker,
	shareOf,
} from "./review-attention";

describe("shareOf and percentText", () => {
	it("says nothing of nothing is nothing", () => {
		expect(shareOf(3, 0)).toBe(0);
		expect(percentText(0)).toBe("0%");
	});

	it("keeps a small share visible", () => {
		expect(percentText(0.0004)).toBe("0.1%");
		expect(percentText(0.004)).toBe("0.4%");
		expect(percentText(0.304)).toBe("30%");
	});
});

const counted = (met: number, notMet: number, doesNotApply: number, couldNotDetermine: number) => ({
	met,
	notMet,
	doesNotApply,
	couldNotDetermine,
});

describe("practiceMarker", () => {
	it("marks a practice that mostly does not apply from 90% does not apply", () => {
		const marked = practiceMarker(counted(1, 1, 18, 0));
		expect(marked).toStrictEqual({
			marker: "NEVER_APPLIES",
			count: 18,
			whole: 20,
		});
		// It applied twice, so its label must not say it never applies.
		expect(PRACTICE_MARKER_DEFS.NEVER_APPLIES.label).toBe("Mostly does not apply");
		expect(practiceMarker(counted(2, 1, 17, 0))).toBeUndefined();
	});

	it("marks a practice that is often undecided from 30% could not determine", () => {
		expect(practiceMarker(counted(10, 4, 0, 6))?.marker).toBe("OFTEN_UNDECIDED");
		expect(practiceMarker(counted(11, 4, 0, 5))).toBeUndefined();
	});

	it("marks nothing below 20 results", () => {
		expect(practiceMarker(counted(0, 0, 19, 0))).toBeUndefined();
	});
});

const row = (name: string, results: ReturnType<typeof counted>, markedIncorrect = 0) => ({
	name,
	results,
	resultsMarkedIncorrect: markedIncorrect,
});

describe("needsALookFirst", () => {
	it("puts the marked practices first, the stronger marker ahead", () => {
		const rows = [
			row("busiest", counted(30, 10, 2, 1)),
			row("undecided", counted(10, 4, 0, 10)),
			row("quiet", counted(3, 1, 0, 0)),
			row("never", counted(0, 0, 20, 0)),
		];
		expect(needsALookFirst(rows).map(({ name }) => name)).toStrictEqual([
			"never",
			"undecided",
			"busiest",
			"quiet",
		]);
	});

	it("orders the unmarked by marked incorrect, then could not determine, then as they came", () => {
		const rows = [
			row("busiest", counted(30, 10, 2, 0)),
			row("one could not determine", counted(10, 4, 0, 1)),
			row("two could not determine", counted(8, 4, 0, 2)),
			row("marked incorrect", counted(3, 1, 0, 0), 1),
			row("quiet", counted(3, 1, 0, 0)),
		];
		expect(needsALookFirst(rows).map(({ name }) => name)).toStrictEqual([
			"marked incorrect",
			"two could not determine",
			"one could not determine",
			"busiest",
			"quiet",
		]);
	});

	it("keeps the order it was given when nothing needs a look", () => {
		const rows = [row("a", counted(5, 0, 0, 0)), row("b", counted(3, 1, 0, 0))];
		expect(needsALookFirst(rows).map(({ name }) => name)).toStrictEqual(["a", "b"]);
	});
});
