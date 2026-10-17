import { assert, describe, expect, it } from "vitest";

import {
	eventBands,
	eventDef,
	eventLabel,
	withdrawnEvents,
} from "@/components/admin/practice-editor/review-events";

import {
	mockDefinitionOptions,
	mockDocumentItemType,
	mockIssueItemType,
	mockPullRequestItemType,
	mockPullRequestReviewItemType,
	mockThreadItemType,
} from "./fixtures";

describe("eventDef", () => {
	it("gives every event of an item kind its own glyph, so the strip reads in greyscale", () => {
		for (const itemType of mockDefinitionOptions.itemTypes) {
			const icons = itemType.events.map((option) => eventDef(option.name).icon);

			expect(new Set(icons).size, itemType.kind).toBe(icons.length);
		}
	});

	it("draws an event this build has never met rather than dropping it", () => {
		const def = eventDef("wiki.page.forked");

		expect(def.phase).toBe("during");
		expect(def.repeats).toBe(false);
	});
});

describe("eventBands", () => {
	it("reads a pull request as start, churn, and two ways to end", () => {
		const bands = eventBands(mockPullRequestItemType.events);

		expect(
			bands.map((band) => [band.phase, band.events.map((option) => option.name)]),
		).toStrictEqual([
			["start", ["scm.pull_request.opened"]],
			[
				"during",
				["scm.pull_request.ready", "scm.pull_request.synchronized", "scm.pull_request.edited"],
			],
			["end", ["scm.pull_request.merged", "scm.pull_request.closed"]],
		]);
	});

	it("starts a pull request review on its own submission, not on its parent pull request", () => {
		const bands = eventBands(mockPullRequestReviewItemType.events);

		expect(bands.map((band) => band.events.map((option) => option.name))).toStrictEqual([
			["scm.pull_request_review.submitted"],
		]);
	});

	it("warns that an issue's middle event repeats, because choosing it is a decision about volume", () => {
		const bands = eventBands(mockIssueItemType.events);

		expect(
			bands.map((band) => [band.phase, band.events.map((option) => option.name)]),
		).toStrictEqual([
			["start", ["scm.issue.opened"]],
			["during", ["scm.issue.updated"]],
			["end", ["scm.issue.closed"]],
		]);
		expect(eventDef("scm.issue.updated").repeats).toBe(true);
	});

	it("gives a document its own three events under the same three bands", () => {
		const bands = eventBands(mockDocumentItemType.events);

		expect(bands.map((band) => band.events.map((option) => option.name))).toStrictEqual([
			["docs.document.published"],
			["docs.document.updated"],
			["docs.document.archived"],
		]);
	});

	it("drops the bands an item kind has nothing in, rather than rendering them empty", () => {
		const bands = eventBands(mockThreadItemType.events);

		expect(bands).toHaveLength(1);
		const [only] = bands;
		assert(only);
		expect(only.phase).toBe("end");
	});
});

describe("withdrawnEvents", () => {
	it("finds nothing while every chosen event is one the item kind offers", () => {
		expect(
			withdrawnEvents(mockPullRequestItemType, [
				"scm.pull_request.opened",
				"scm.pull_request.merged",
			]),
		).toStrictEqual([]);
	});

	it("names an event nothing on the wire explains in words, never by its id", () => {
		const [withdrawn] = withdrawnEvents(mockDocumentItemType, ["docs.document.forked"]);

		expect(withdrawn?.name).toBe("docs.document.forked");
		expect(withdrawn?.label).toBe("An event this version no longer offers");
		expect(eventLabel("docs.document.forked", mockDocumentItemType.events)).toBe(
			"An event this version no longer offers",
		);
	});
});
