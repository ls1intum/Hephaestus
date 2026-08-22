import { describe, expect, it } from "vitest";
import type { ObservationList, PracticeAreaReviewMoment } from "@/api/types.gen";
import {
	observationsToReviewedArtifacts,
	reviewMomentsToReviewedArtifacts,
} from "./review-history";

function observation(overrides: Partial<ObservationList> = {}): ObservationList {
	return {
		id: "00000000-0000-0000-0000-000000000001",
		practiceSlug: "records-decisions",
		practiceName: "Record significant decisions",
		artifactKind: "PULL_REQUEST",
		artifactId: 902,
		summary: "Decision is explained",
		presence: "PRESENT",
		assessment: "GOOD",
		claimCurrentness: "CURRENT",
		origin: "LIVE",
		observedAt: new Date("2026-08-12T10:26:00Z"),
		...overrides,
	};
}

describe("observationsToReviewedArtifacts", () => {
	it("groups findings from one delivery into one artifact review moment", () => {
		const artifacts = observationsToReviewedArtifacts(
			[
				observation(),
				observation({
					id: "00000000-0000-0000-0000-000000000002",
					practiceSlug: "keeps-docs-current",
					practiceName: "Keep documentation current",
				}),
				observation({
					id: "00000000-0000-0000-0000-000000000003",
					observedAt: new Date("2026-08-09T16:40:00Z"),
				}),
			],
			"GITHUB",
		);

		expect(artifacts).toHaveLength(1);
		expect(artifacts[0].provider).toBe("GITHUB");
		expect(artifacts[0].runs).toHaveLength(2);
		expect(artifacts[0].runs[0].findings).toHaveLength(2);
		expect(artifacts[0].runs[1].findings).toHaveLength(1);
	});

	it("keeps separate artifacts and maps conversations to Slack", () => {
		const artifacts = observationsToReviewedArtifacts(
			[
				observation(),
				observation({
					id: "00000000-0000-0000-0000-000000000004",
					artifactKind: "CONVERSATION_THREAD",
					artifactId: 42,
				}),
			],
			"GITLAB",
		);

		expect(artifacts).toHaveLength(2);
		expect(artifacts.map((artifact) => artifact.provider)).toEqual(["GITLAB", "SLACK"]);
	});

	it("keeps server review boundaries, artifact metadata, and concrete finding titles", () => {
		const moments: PracticeAreaReviewMoment[] = [
			{
				reviewId: "00000000-0000-0000-0000-000000000101",
				reviewedAt: new Date("2026-08-12T10:26:00Z"),
				artifact: {
					type: "PULL_REQUEST",
					id: 902,
					provider: "GITHUB",
					number: 902,
					title: "Split the catalog loader per workspace",
					repositoryName: "HephaestusTest/practice-validation",
					url: "https://github.com/HephaestusTest/practice-validation/pull/902",
				},
				findings: [
					{
						observationId: "00000000-0000-0000-0000-000000000102",
						feedbackId: "00000000-0000-0000-0000-000000000103",
						helpful: true,
						practiceSlug: "records-decisions",
						practiceName: "Record significant decisions",
						title: "The workspace trade-off is documented",
						presence: "PRESENT",
						assessment: "GOOD",
					},
				],
			},
		];

		const [artifact] = reviewMomentsToReviewedArtifacts(moments);

		expect(artifact.number).toBe(902);
		expect(artifact.title).toBe("Split the catalog loader per workspace");
		expect(artifact.runs).toHaveLength(1);
		expect(artifact.runs[0].findings[0]).toMatchObject({
			title: "The workspace trade-off is documented",
			assessment: "GOOD",
			feedbackId: "00000000-0000-0000-0000-000000000103",
			helpful: true,
		});
	});
});
