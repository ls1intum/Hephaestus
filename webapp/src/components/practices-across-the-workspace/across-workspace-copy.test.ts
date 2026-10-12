import { describe, expect, it } from "vitest";

import type { WorkspaceSplit } from "@/api/types.gen";

import { barsHint, FIGURES_PLAIN, splitDescription } from "./across-workspace-copy";

const split = (
	needsAttention: number,
	mixedFeedback: number,
	goingWell: number,
	noneYet: number,
): WorkspaceSplit => ({
	parts: [
		{ standing: "DEVELOPING", developers: needsAttention },
		{ standing: "MIXED", developers: mixedFeedback },
		{ standing: "STRENGTH", developers: goingWell },
	],
	noneYet,
	developers: needsAttention + mixedFeedback + goingWell + noneYet,
});

describe("splitDescription", () => {
	it("names the whole, every count in the server's order, and the part the marker is on", () => {
		expect(splitDescription(split(6, 7, 7, 8), "MIXED")).toBe(
			"28 developers with a current standing in this workspace: 6 Needs attention, 7 Mixed feedback, 7 Going well, 8 none yet. The You marker is on Mixed feedback.",
		);
	});

	it.each(["NOT_OBSERVED", "NO_OPPORTUNITY"] as const)(
		"puts a reader whose standing is %s on none yet",
		(standing) => {
			expect(splitDescription(split(6, 7, 7, 8), standing)).toMatch(
				/The You marker is on none yet\.$/u,
			);
		},
	);

	it("marks no one when the server sends no marker", () => {
		expect(splitDescription(split(6, 7, 7, 8), undefined)).toMatch(/, 8 none yet\.$/u);
	});

	it("draws parts of one and none like any other", () => {
		expect(splitDescription(split(1, 0, 2, 0), "STRENGTH")).toBe(
			"3 developers with a current standing in this workspace: 1 Needs attention, 0 Mixed feedback, 2 Going well, 0 none yet. The You marker is on Going well.",
		);
	});

	it("says that nobody has a standing when the split counts nobody", () => {
		expect(splitDescription(split(0, 0, 0, 0), undefined)).toBe("No developer has a standing yet.");
	});
});

describe("FIGURES_PLAIN", () => {
	it("holds no count and no window, so it reads the same for every filter", () => {
		// The words only: a swatch's class name may carry digits.
		const words = FIGURES_PLAIN.flatMap((group) => [
			group.heading,
			...group.lines.flatMap((line) => [line.term, line.text, JSON.stringify(line.second)]),
		]);
		expect(words.join(" ")).not.toMatch(/\d|days|so far/u);
	});

	it("answers four questions that do not overlap, each line led by its key term", () => {
		expect(
			FIGURES_PLAIN.map((group) => [group.heading, group.lines.map((line) => line.term)]),
		).toStrictEqual([
			["What you see", ["Grey band:", "Blue marker:"]],
			["How to read it", ["Left of the band:", "Better or worse:"]],
			["When the band is missing", ["No band:"]],
			["What counts", ["Open feedback:"]],
		]);
	});

	it("draws the band's and the marker's colours beside their lines", () => {
		const [whatYouSee] = FIGURES_PLAIN;
		expect(whatYouSee?.lines.every((line) => line.swatchClass !== undefined)).toBe(true);
	});
});

describe("barsHint", () => {
	it.each(["group", "practice"] as const)(
		"tells a reader of a %s what a small count can show",
		(scope) => {
			expect(barsHint(scope)).toContain(`current standing in the ${scope},`);
			expect(barsHint(scope)).toMatch(/So a small count can let others tell where you stand\.$/u);
		},
	);
});
