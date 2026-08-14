import { describe, expect, it } from "vitest";
import {
	evidenceQualityRequirement,
	groupEvidenceSources,
} from "@/components/admin/practice-catalog/evidence-presentation";
import {
	mockDocumentWorkType,
	mockPracticeDefinitionOptions,
	mockPullRequestWorkType,
} from "@/mocks/fixtures/practice";

describe("groupEvidenceSources", () => {
	it("files every source the catalogue ships under a heading that names it", () => {
		const orphans = mockPracticeDefinitionOptions.workTypes.flatMap((workType) =>
			groupEvidenceSources(workType.allowedSources)
				.filter((group) => group.family === "other")
				.flatMap((group) => group.sources.map((source) => source.sourceKind)),
		);

		expect(orphans).toEqual([]);
	});

	it("splits a pull request's eleven sources into three short decisions", () => {
		expect(
			groupEvidenceSources(mockPullRequestWorkType.allowedSources).map((group) => [
				group.family,
				group.sources.length,
			]),
		).toEqual([
			["work", 5],
			["around", 4],
			["history", 2],
		]);
	});

	it("drops a family the work type has no source for", () => {
		expect(
			groupEvidenceSources(mockDocumentWorkType.allowedSources).map((group) => group.family),
		).toEqual(["work", "history"]);
	});

	it("keeps a source this build has not been taught, under its own heading", () => {
		const groups = groupEvidenceSources([
			{
				sourceKind: "wiki.page.body",
				displayName: "Wiki page",
				description: "A page of the wiki.",
				privacyClass: "INTERNAL",
				requiredQuality: "ANY_CAPTURE",
				supportsExhaustiveEvidence: false,
			},
		]);

		expect(groups.map((group) => group.family)).toEqual(["other"]);
	});
});

describe("evidenceQualityRequirement", () => {
	it("says nothing where the answer is the norm", () => {
		expect(evidenceQualityRequirement("ANY_CAPTURE")).toBeNull();
		expect(evidenceQualityRequirement(undefined)).toBeNull();
	});

	it("names the stricter contracts, which are the ones that turn reviews away", () => {
		expect(evidenceQualityRequirement("COMPLETE")).toBe("Must be captured whole");
		expect(evidenceQualityRequirement("COMPLETE_AND_NON_EMPTY")).toBe(
			"Must be captured whole, and not be empty",
		);
	});
});
