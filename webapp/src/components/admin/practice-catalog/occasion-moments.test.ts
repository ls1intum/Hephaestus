import { describe, expect, it } from "vitest";
import {
	isManualRequestSignal,
	lifecycleSignals,
	manualRequestSignal,
	momentBands,
	momentDef,
} from "@/components/admin/practice-catalog/occasion-moments";
import {
	mockConversationWorkType,
	mockDocumentWorkType,
	mockIssueWorkType,
	mockPracticeDefinitionOptions,
	mockPullRequestWorkType,
} from "@/mocks/fixtures/practice";

describe("momentDef", () => {
	it("gives every moment of a work type its own glyph, so the strip reads in greyscale", () => {
		for (const workType of mockPracticeDefinitionOptions.workTypes) {
			const icons = lifecycleSignals(workType.signals).map(
				(option) => momentDef(option.signal).icon,
			);

			expect(new Set(icons).size, workType.artifactKind).toBe(icons.length);
		}
	});

	it("draws a moment this build has never met rather than dropping it", () => {
		const def = momentDef("wiki.page.forked");

		expect(def.phase).toBe("during");
		expect(def.repeats).toBe(false);
	});
});

describe("momentBands", () => {
	it("reads a pull request as start, churn, and two ways to end", () => {
		const bands = momentBands(lifecycleSignals(mockPullRequestWorkType.signals));

		expect(bands.map((band) => [band.phase, band.moments.map((moment) => moment.signal)])).toEqual([
			["start", ["scm.pull_request.opened"]],
			[
				"during",
				["scm.pull_request.ready", "scm.pull_request.synchronized", "scm.pull_request.reviewed"],
			],
			["end", ["scm.pull_request.merged", "scm.pull_request.closed"]],
		]);
	});

	it("gives a document its own three moments under the same three bands", () => {
		const bands = momentBands(lifecycleSignals(mockDocumentWorkType.signals));

		expect(bands.map((band) => band.moments.map((moment) => moment.signal))).toEqual([
			["docs.document.published"],
			["docs.document.updated"],
			["docs.document.archived"],
		]);
	});

	it("drops the bands a work type has nothing in, rather than rendering them empty", () => {
		const bands = momentBands(lifecycleSignals(mockConversationWorkType.signals));

		expect(bands).toHaveLength(1);
		expect(bands[0].phase).toBe("end");
	});
});

describe("isManualRequestSignal", () => {
	it("recognises the hand-asked review on every kind that offers one", () => {
		expect(isManualRequestSignal("scm.pull_request.manual_review")).toBe(true);
		expect(isManualRequestSignal("scm.issue.manual_review")).toBe(true);
	});

	it("does not mistake a lifecycle moment for one", () => {
		expect(isManualRequestSignal("scm.pull_request.reviewed")).toBe(false);
		expect(isManualRequestSignal("docs.document.published")).toBe(false);
	});
});

describe("lifecycleSignals", () => {
	it("keeps the hand-asked review off the strip, because binding it decides nothing", () => {
		expect(lifecycleSignals(mockPullRequestWorkType.signals).map((o) => o.signal)).not.toContain(
			"scm.pull_request.manual_review",
		);
		expect(manualRequestSignal(mockIssueWorkType.signals)?.signal).toBe("scm.issue.manual_review");
	});

	it("leaves a work type that offers no such review untouched", () => {
		expect(lifecycleSignals(mockDocumentWorkType.signals)).toHaveLength(3);
		expect(manualRequestSignal(mockDocumentWorkType.signals)).toBeUndefined();
	});
});
