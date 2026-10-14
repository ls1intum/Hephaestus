import { describe, expect, it } from "vitest";

import { appliesWhenText, parseAppliesWhen } from "./applies-when";
import { mockIssueItemType, mockPullRequestItemType } from "./fixtures";

const swift = {
	anyOf: [{ changedPath: ["**/*.swift"] }],
	otherwise: "the change has no Swift code",
};

function draft(anyOf: unknown, otherwise = swift.otherwise) {
	return { clauses: JSON.stringify(anyOf), otherwise };
}

describe("parseAppliesWhen", () => {
	it("keeps complete conditions and the sentence for when they do not hold", () => {
		expect(parseAppliesWhen(draft(swift.anyOf), mockPullRequestItemType)).toStrictEqual({
			value: swift,
		});
	});

	it("reads a blank field as no conditions, whatever Otherwise still says", () => {
		expect(
			parseAppliesWhen({ clauses: " ", otherwise: "left over" }, mockPullRequestItemType),
		).toStrictEqual({ value: null });
	});

	it("refuses malformed, unknown and empty clauses instead of discarding them", () => {
		expect(parseAppliesWhen({ clauses: "{", otherwise: "x" }, undefined).clausesError).toBe(
			"Enter the conditions as valid JSON.",
		);
		expect(
			parseAppliesWhen(draft([{ changedPath: ["**/*.swift"], typo: true }]), undefined)
				.clausesError,
		).toBeDefined();
		expect(parseAppliesWhen(draft([{ changedPath: [" "] }]), undefined).clausesError).toBeDefined();
		expect(parseAppliesWhen(draft([]), undefined).clausesError).toBeDefined();
	});

	it("refuses a changed path that leaves the repository", () => {
		expect(
			parseAppliesWhen(draft([{ changedPath: ["../secrets"] }]), undefined).clausesError,
		).toBeDefined();
	});

	it("requires the Otherwise sentence once there are conditions", () => {
		expect(parseAppliesWhen(draft(swift.anyOf, " "), mockPullRequestItemType).otherwiseError).toBe(
			"Say in at most 300 characters why the practice does not apply otherwise.",
		);
	});

	it("permits a condition only when the item type can check it", () => {
		expect(parseAppliesWhen(draft(swift.anyOf), mockIssueItemType).clausesError).toBe(
			"This kind of item cannot check one of these conditions.",
		);
	});
});

describe("appliesWhenText", () => {
	it("round-trips the saved conditions", () => {
		expect(
			parseAppliesWhen({ clauses: appliesWhenText(swift), otherwise: swift.otherwise }, undefined)
				.value,
		).toStrictEqual(swift);
		expect(appliesWhenText(null)).toBe("");
	});
});
