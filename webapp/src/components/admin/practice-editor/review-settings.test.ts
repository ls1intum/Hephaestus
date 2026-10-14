import { describe, expect, it } from "vitest";

import {
	normalizeReviewOn,
	orderedItemTypes,
	readNeedOf,
	readsProblem,
	recommendedReviewOn,
	reviewSettingsProblem,
	withReadNeed,
} from "@/components/admin/practice-editor/review-settings";

import {
	mockDefinitionOptions,
	mockIssueItemType,
	mockPullRequestItemType,
	mockPullRequestReviewItemType,
} from "./fixtures";

const core = { source: "scm.pull-request.core", need: "REQUIRED" } as const;

describe("normalizeReviewOn", () => {
	it("puts Review on in the shape the server stores it so an untouched form is not dirty", () => {
		expect(
			normalizeReviewOn({
				events: ["scm.pull_request.ready", "scm.pull_request.opened", "scm.pull_request.ready"],
				state: { state: ["OPEN", "CLOSED", "OPEN"], draftStatus: ["NOT_DRAFT"] },
			}),
		).toStrictEqual({
			events: ["scm.pull_request.opened", "scm.pull_request.ready"],
			state: { draftStatus: ["NOT_DRAFT"], state: ["CLOSED", "OPEN"] },
		});
	});
});

describe("recommendedReviewOn", () => {
	it("takes the recommended events and leaves every state open", () => {
		expect(recommendedReviewOn(mockPullRequestItemType)).toStrictEqual({
			events: ["scm.pull_request.opened", "scm.pull_request.ready"],
			state: {},
		});
	});
});

describe("read needs", () => {
	it("reads a source that is not chosen as not used, and keeps the list sorted", () => {
		expect(readNeedOf([core], "scm.pull-request.diff")).toBe("NOT_USED");
		const sources = withReadNeed([core], "scm.pull-request.comments", "OPTIONAL");
		expect(sources.map((entry) => entry.source)).toStrictEqual([
			"scm.pull-request.comments",
			"scm.pull-request.core",
		]);
		expect(withReadNeed(sources, "scm.pull-request.core", "NOT_USED")).toStrictEqual([
			{ source: "scm.pull-request.comments", need: "OPTIONAL" },
		]);
	});

	it("refuses an automated review that would read only optional sources", () => {
		expect(
			readsProblem(
				[{ source: "scm.repository.tree", need: "OPTIONAL" }],
				mockPullRequestItemType.readSources,
			),
		).toBe("Set at least one source to Required. A review cannot run without one.");
		expect(
			readsProblem(
				[{ source: "scm.repository.tree", need: "COMPLETE" }],
				mockPullRequestItemType.readSources,
			),
		).toBeUndefined();
	});

	it("refuses Complete on a source that cannot be captured whole", () => {
		expect(
			readsProblem(
				[core, { source: "scm.linked-work-items", need: "COMPLETE" }],
				mockPullRequestItemType.readSources,
			),
		).toBe("Linked work items cannot be captured whole. Change it to Required or Optional.");
	});
});

describe("reviewSettingsProblem", () => {
	const reviewOn = { events: ["scm.pull_request.opened"], state: {} };

	it("accepts a practice with no event, which runs only when someone requests a review", () => {
		expect(
			reviewSettingsProblem(
				{ events: [], state: {} },
				[core],
				"HEPHAESTUS",
				mockPullRequestItemType,
			),
		).toBeUndefined();
	});

	it("names an event the item kind does not offer", () => {
		expect(
			reviewSettingsProblem(
				{ events: ["scm.pull_request.reviewed"], state: {} },
				[core],
				"HEPHAESTUS",
				mockPullRequestItemType,
			)?.focusId,
		).toBe("practice-review-on-events");
	});

	it("refuses a state value the item kind does not offer", () => {
		expect(
			reviewSettingsProblem(
				{ ...reviewOn, state: { draftStatus: ["ARCHIVED"] } },
				[core],
				"HEPHAESTUS",
				mockPullRequestItemType,
			)?.message,
		).toBe("Choose states this kind of item offers.");
	});

	it("checks reads only while Hephaestus reviews the practice", () => {
		expect(
			reviewSettingsProblem(reviewOn, [], "HEPHAESTUS", mockPullRequestItemType)?.focusId,
		).toBe("practice-review-on-reads");
		expect(reviewSettingsProblem(reviewOn, [], "PEOPLE", mockPullRequestItemType)).toBeUndefined();
		expect(reviewSettingsProblem(reviewOn, [], "NOBODY", mockPullRequestItemType)).toBeUndefined();
	});

	it("refuses a source the item kind does not read", () => {
		expect(
			reviewSettingsProblem(
				{ events: [], state: {} },
				[{ source: "scm.pull-request.core", need: "REQUIRED" }],
				"HEPHAESTUS",
				mockIssueItemType,
			)?.message,
		).toBe("One chosen source is not offered for this kind of item. Turn it off to continue.");
	});
});

describe("orderedItemTypes", () => {
	it("lists the kinds in the order every list of kinds keeps", () => {
		expect(
			orderedItemTypes({
				...mockDefinitionOptions,
				itemTypes: [mockIssueItemType, mockPullRequestReviewItemType, mockPullRequestItemType],
			}).map((option) => option.kind),
		).toStrictEqual(["scm.pull_request", "scm.pull_request_review", "scm.issue"]);
	});
});
