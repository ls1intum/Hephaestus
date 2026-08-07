import type {
	PracticeAutomatedReviewPolicy,
	PracticeAutomatedReviewValidation,
	PracticeDefinitionOptions,
} from "@/api/types.gen";

export const mockAuthorDeclaredEvidenceValidation = {
	status: "AUTHOR_DECLARED",
	sourceContractVersion: "1.0.0",
	policyDigest: "0".repeat(64),
	reviewRuleFingerprint: `v2:${"0".repeat(64)}`,
} satisfies PracticeAutomatedReviewValidation;

export const mockPullRequestEvidence = {
	sourceContractVersion: "1.0.0",
	evidenceProfile: "pull-request-review",
	automatedReview: {
		mode: "LANGUAGE_MODEL",
		evidenceSufficiency: "SUFFICIENT_WHEN_REQUIREMENTS_MET",
	},
	requiredEvidence: [
		{ sourceKind: "scm.pull-request.core", completeness: "COMPLETE" },
		{
			sourceKind: "scm.pull-request.diff",
			completeness: "COMPLETE",
			content: "NON_EMPTY",
		},
	],
	optionalContext: [{ sourceKind: "scm.pull-request.comments" }],
	whenEvidenceIsInsufficient: "SKIP_AUTOMATED_REVIEW",
	knownLimitations: [
		{
			code: "RUNTIME_BEHAVIOR_NOT_OBSERVED",
			description: "Repository evidence does not establish behavior in a deployed runtime.",
		},
	],
} satisfies PracticeAutomatedReviewPolicy;

const mockIssueEvidence = {
	sourceContractVersion: "1.0.0",
	evidenceProfile: "issue-review",
	automatedReview: {
		mode: "LANGUAGE_MODEL",
		evidenceSufficiency: "SUFFICIENT_WHEN_REQUIREMENTS_MET",
	},
	requiredEvidence: [{ sourceKind: "scm.issue.core", completeness: "COMPLETE" }],
	optionalContext: [{ sourceKind: "scm.issue.comments" }],
	whenEvidenceIsInsufficient: "SKIP_AUTOMATED_REVIEW",
	knownLimitations: [
		{
			code: "IMPLEMENTATION_NOT_OBSERVED",
			description:
				"Issue evidence does not establish whether the described work was implemented correctly.",
		},
	],
} satisfies PracticeAutomatedReviewPolicy;

const mockConversationEvidence = {
	sourceContractVersion: "1.0.0",
	evidenceProfile: "conversation-review",
	automatedReview: {
		mode: "LANGUAGE_MODEL",
		evidenceSufficiency: "SUFFICIENT_WHEN_REQUIREMENTS_MET",
	},
	requiredEvidence: [
		{
			sourceKind: "slack.conversation.thread",
			completeness: "COMPLETE",
		},
	],
	optionalContext: [],
	whenEvidenceIsInsufficient: "SKIP_AUTOMATED_REVIEW",
	knownLimitations: [
		{
			code: "PRIVATE_CONTEXT_NOT_OBSERVED",
			description:
				"The captured thread does not include decisions or context shared outside the conversation.",
		},
	],
} satisfies PracticeAutomatedReviewPolicy;

export const mockPracticeDefinitionOptions = {
	workTypes: [
		{
			artifactKind: "scm.pull_request",
			triggerEvents: [
				{
					event: "PullRequestCreated",
					displayName: "Pull or merge request is opened",
					recommended: true,
				},
				{ event: "PullRequestReady", displayName: "Marked ready for review", recommended: true },
				{
					event: "PullRequestSynchronized",
					displayName: "New commits are pushed",
					recommended: true,
				},
				{ event: "ReviewSubmitted", displayName: "A review is submitted", recommended: false },
				{
					event: "PullRequestMerged",
					displayName: "Pull or merge request is merged",
					recommended: false,
				},
				{
					event: "PullRequestClosed",
					displayName: "Pull or merge request is closed without merging",
					recommended: false,
				},
			],
			supportedAutomatedReviewModes: ["LANGUAGE_MODEL"],
			recommendedRequirements: mockPullRequestEvidence,
			allowedSources: [
				{
					sourceKind: "scm.pull-request.core",
					displayName: "Pull request details",
					description: "Pull request metadata and commit subjects for the reviewed pull request.",
					privacyClass: "PERSONAL",
					supportsComplete: true,
					supportsEmpty: false,
				},
				{
					sourceKind: "scm.pull-request.diff",
					displayName: "Code changes",
					description: "Code changes in the reviewed pull request.",
					privacyClass: "PERSONAL",
					supportsComplete: true,
					supportsEmpty: true,
				},
				{
					sourceKind: "scm.pull-request.comments",
					displayName: "Inline review comments",
					description: "Inline review comments mirrored by the application.",
					privacyClass: "PERSONAL",
					supportsComplete: true,
					supportsEmpty: true,
				},
			],
		},
		{
			artifactKind: "scm.issue",
			triggerEvents: [
				{ event: "IssueCreated", displayName: "Issue is opened", recommended: true },
				{ event: "IssueLabeled", displayName: "Issue is labeled", recommended: true },
				{ event: "IssueClosed", displayName: "Issue is closed", recommended: false },
			],
			supportedAutomatedReviewModes: ["LANGUAGE_MODEL"],
			recommendedRequirements: mockIssueEvidence,
			allowedSources: [
				{
					sourceKind: "scm.issue.core",
					displayName: "Issue details",
					description: "Issue metadata and rendered description.",
					privacyClass: "PERSONAL",
					supportsComplete: true,
					supportsEmpty: false,
				},
				{
					sourceKind: "scm.issue.comments",
					displayName: "Issue comments",
					description: "Issue discussion comments.",
					privacyClass: "PERSONAL",
					supportsComplete: true,
					supportsEmpty: true,
				},
			],
		},
		{
			artifactKind: "chat.conversation_thread",
			triggerEvents: [],
			supportedAutomatedReviewModes: ["LANGUAGE_MODEL"],
			recommendedRequirements: mockConversationEvidence,
			allowedSources: [
				{
					sourceKind: "slack.conversation.thread",
					displayName: "Slack thread",
					description: "Ordered human messages from one Slack thread.",
					privacyClass: "PERSONAL",
					supportsComplete: true,
					supportsEmpty: false,
				},
			],
		},
	],
} satisfies PracticeDefinitionOptions;
