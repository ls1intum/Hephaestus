import type { PracticeEvidenceDeclaration } from "@/api/types.gen";

export const mockPullRequestEvidence = {
	sourceContractVersion: "1.0.0",
	profile: "pull-request-review",
	required: [
		{ sourceKind: "scm.pull-request.core", completeness: "COMPLETE", freshness: "CURRENT" },
	],
	optional: [{ sourceKind: "scm.pull-request.comments", completeness: "ANY", freshness: "ANY" }],
	onUnsatisfied: "DECLINE_SEMANTIC_JUDGMENT",
	blindSpots: [
		{
			code: "RUNTIME_BEHAVIOR_NOT_OBSERVED",
			summary: "Repository evidence does not establish behavior in a deployed runtime.",
		},
	],
} satisfies PracticeEvidenceDeclaration;
