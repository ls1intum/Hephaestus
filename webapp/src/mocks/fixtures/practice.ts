import type {
	PracticeEvidenceAuthoring,
	PracticeEvidenceDeclaration,
	PracticeEvidenceValidation,
} from "@/api/types.gen";

export const mockAuthorDeclaredEvidenceValidation = {
	status: "AUTHOR_DECLARED",
	sourceContractVersion: "1.0.0",
	declarationDigest: "0".repeat(64),
} satisfies PracticeEvidenceValidation;

export const mockPullRequestEvidence = {
	sourceContractVersion: "1.0.0",
	profile: "pull-request-review",
	observability: "SEMANTIC",
	required: [
		{ sourceKind: "scm.pull-request.core", completeness: "COMPLETE", freshness: "CURRENT" },
		{ sourceKind: "scm.pull-request.diff", completeness: "COMPLETE", freshness: "CURRENT" },
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

const mockIssueEvidence = {
	sourceContractVersion: "1.0.0",
	profile: "issue-review",
	observability: "SEMANTIC",
	required: [{ sourceKind: "scm.issue.core", completeness: "COMPLETE", freshness: "CURRENT" }],
	optional: [{ sourceKind: "scm.issue.comments", completeness: "ANY", freshness: "ANY" }],
	onUnsatisfied: "DECLINE_SEMANTIC_JUDGMENT",
	blindSpots: [
		{
			code: "IMPLEMENTATION_NOT_OBSERVED",
			summary: "Issue evidence does not establish whether the work was implemented correctly.",
		},
	],
} satisfies PracticeEvidenceDeclaration;

const mockConversationEvidence = {
	sourceContractVersion: "1.0.0",
	profile: "conversation-review",
	observability: "SEMANTIC",
	required: [
		{
			sourceKind: "slack.conversation.thread",
			completeness: "COMPLETE",
			freshness: "CURRENT",
		},
	],
	optional: [],
	onUnsatisfied: "DECLINE_SEMANTIC_JUDGMENT",
	blindSpots: [
		{
			code: "PRIVATE_CONTEXT_NOT_OBSERVED",
			summary: "The captured thread does not include context shared elsewhere.",
		},
	],
} satisfies PracticeEvidenceDeclaration;

export const mockPracticeEvidenceAuthoring = {
	artifacts: [
		{
			artifactType: "PULL_REQUEST",
			baseline: mockPullRequestEvidence,
			sources: [
				{
					sourceKind: "scm.pull-request.core",
					description: "Pull request metadata and commit subjects for the reviewed artifact.",
					privacyClass: "PERSONAL",
					supportsComplete: true,
					supportsCurrent: true,
					supportsEmpty: false,
					authorizedForDetection: true,
				},
				{
					sourceKind: "scm.pull-request.diff",
					description: "Code changes in the reviewed pull request.",
					privacyClass: "PERSONAL",
					supportsComplete: true,
					supportsCurrent: true,
					supportsEmpty: true,
					authorizedForDetection: true,
				},
				{
					sourceKind: "scm.pull-request.comments",
					description: "Inline review comments mirrored by the application.",
					privacyClass: "PERSONAL",
					supportsComplete: true,
					supportsCurrent: false,
					supportsEmpty: true,
					authorizedForDetection: true,
				},
			],
		},
		{
			artifactType: "ISSUE",
			baseline: mockIssueEvidence,
			sources: [
				{
					sourceKind: "scm.issue.core",
					description: "Issue metadata and rendered description.",
					privacyClass: "PERSONAL",
					supportsComplete: true,
					supportsCurrent: true,
					supportsEmpty: false,
					authorizedForDetection: true,
				},
				{
					sourceKind: "scm.issue.comments",
					description: "Issue discussion comments.",
					privacyClass: "PERSONAL",
					supportsComplete: true,
					supportsCurrent: false,
					supportsEmpty: true,
					authorizedForDetection: true,
				},
			],
		},
		{
			artifactType: "CONVERSATION_THREAD",
			baseline: mockConversationEvidence,
			sources: [
				{
					sourceKind: "slack.conversation.thread",
					description: "Ordered human messages from one Slack thread.",
					privacyClass: "PERSONAL",
					supportsComplete: true,
					supportsCurrent: true,
					supportsEmpty: false,
					authorizedForDetection: false,
				},
			],
		},
	],
} satisfies PracticeEvidenceAuthoring;
