import { describe, expect, it } from "vitest";
import {
	evidenceQualityRequirement,
	groupEvidenceSources,
} from "@/components/admin/practice-catalog/evidence-presentation";
import { knownEvidenceSourceKinds } from "@/components/practice-vocabulary/evidence-source-defs";
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

	// The catalogue fixture above is one workspace's sources; this is every kind the app has words
	// for. The two tables are edited in different directories, and a kind added to the vocabulary but
	// not to a family degrades quietly — it keeps its own label and files itself under "Other
	// sources", which reads as a deliberate answer rather than as a gap.
	it("gives every kind the vocabulary knows a family, not just the ones this catalogue ships", () => {
		const unfiled = knownEvidenceSourceKinds().filter(
			(sourceKind) =>
				groupEvidenceSources([
					{
						sourceKind,
						displayName: sourceKind,
						description: "",
						selectionScope: "",
						privacyClass: "INTERNAL",
						requiredQuality: "ANY_CAPTURE",
						supportsExhaustiveEvidence: false,
					},
				])[0]?.family === "other",
		);

		expect(unfiled).toEqual([]);
	});

	// Grouping is a partition, and both ways it can stop being one are silent: a source filed under no
	// family disappears from the screen entirely, and a family emitted in source order buries "the
	// work itself" under "this person's history". Asserted against the input's own keys rather than
	// against a count, so adding a source to the catalogue does not have to be retyped here — a count
	// that has to be retyped is a count that gets retyped without being read.
	it("partitions a pull request's sources, the work itself first and history last", () => {
		const groups = groupEvidenceSources(mockPullRequestWorkType.allowedSources);
		const filed = groups.flatMap((group) => group.sources.map((source) => source.sourceKind));

		expect(groups.map((group) => group.family)).toEqual(["work", "around", "history"]);
		expect([...filed].sort()).toEqual(
			mockPullRequestWorkType.allowedSources.map((source) => source.sourceKind).sort(),
		);
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
				selectionScope: "One page, whole.",
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
