import type {
	PracticeAutomatedAssessmentPolicy,
	PracticeAutomatedAssessmentValidation,
	PracticeEvidenceOptions,
} from "@/api/types.gen";

export const mockAuthorDeclaredEvidenceValidation = {
	status: "AUTHOR_DECLARED",
	sourceContractVersion: "1.0.0",
	policyDigest: "0".repeat(64),
	reviewRuleFingerprint: `v2:${"0".repeat(64)}`,
} satisfies PracticeAutomatedAssessmentValidation;

export const mockPullRequestEvidence = {
	sourceContractVersion: "1.0.0",
	evidenceProfile: "pull-request-review",
	automatedAssessment: {
		mode: "LANGUAGE_MODEL",
		evidenceSufficiency: "SUFFICIENT_WHEN_REQUIREMENTS_MET",
	},
	requiredEvidence: [
		{ sourceKind: "scm.pull-request.core", completeness: "COMPLETE", freshness: "CURRENT" },
		{ sourceKind: "scm.pull-request.diff", completeness: "COMPLETE", freshness: "CURRENT" },
	],
	optionalContext: [{ sourceKind: "scm.pull-request.comments" }],
	whenEvidenceIsInsufficient: "SKIP_AUTOMATED_ASSESSMENT",
	knownLimitations: [
		{
			code: "RUNTIME_BEHAVIOR_NOT_OBSERVED",
			description: "Repository evidence does not establish behavior in a deployed runtime.",
		},
	],
} satisfies PracticeAutomatedAssessmentPolicy;

const mockIssueEvidence = {
	sourceContractVersion: "1.0.0",
	evidenceProfile: "issue-review",
	automatedAssessment: {
		mode: "LANGUAGE_MODEL",
		evidenceSufficiency: "SUFFICIENT_WHEN_REQUIREMENTS_MET",
	},
	requiredEvidence: [
		{ sourceKind: "scm.issue.core", completeness: "COMPLETE", freshness: "CURRENT" },
	],
	optionalContext: [{ sourceKind: "scm.issue.comments" }],
	whenEvidenceIsInsufficient: "SKIP_AUTOMATED_ASSESSMENT",
	knownLimitations: [
		{
			code: "IMPLEMENTATION_NOT_OBSERVED",
			description:
				"Issue evidence does not establish whether the described work was implemented correctly.",
		},
	],
} satisfies PracticeAutomatedAssessmentPolicy;

const mockConversationEvidence = {
	sourceContractVersion: "1.0.0",
	evidenceProfile: "conversation-review",
	automatedAssessment: {
		mode: "LANGUAGE_MODEL",
		evidenceSufficiency: "SUFFICIENT_WHEN_REQUIREMENTS_MET",
	},
	requiredEvidence: [
		{
			sourceKind: "slack.conversation.thread",
			completeness: "COMPLETE",
			freshness: "CURRENT",
		},
	],
	optionalContext: [],
	whenEvidenceIsInsufficient: "SKIP_AUTOMATED_ASSESSMENT",
	knownLimitations: [
		{
			code: "PRIVATE_CONTEXT_NOT_OBSERVED",
			description:
				"The captured thread does not include decisions or context shared outside the conversation.",
		},
	],
} satisfies PracticeAutomatedAssessmentPolicy;

export const mockPracticeEvidenceOptions = {
	workTypes: [
		{
			artifactType: "PULL_REQUEST",
			supportedAutomatedAssessmentModes: ["LANGUAGE_MODEL"],
			recommendedRequirements: mockPullRequestEvidence,
			allowedSources: [
				{
					sourceKind: "scm.pull-request.core",
					displayName: "Pull request details",
					description: "Pull request metadata and commit subjects for the reviewed pull request.",
					privacyClass: "PERSONAL",
					supportsComplete: true,
					supportsCurrent: true,
					supportsEmpty: false,
					authorizedForAutomatedAssessment: true,
				},
				{
					sourceKind: "scm.pull-request.diff",
					displayName: "Code changes",
					description: "Code changes in the reviewed pull request.",
					privacyClass: "PERSONAL",
					supportsComplete: true,
					supportsCurrent: true,
					supportsEmpty: true,
					authorizedForAutomatedAssessment: true,
				},
				{
					sourceKind: "scm.pull-request.comments",
					displayName: "Inline review comments",
					description: "Inline review comments mirrored by the application.",
					privacyClass: "PERSONAL",
					supportsComplete: true,
					supportsCurrent: false,
					supportsEmpty: true,
					authorizedForAutomatedAssessment: true,
				},
			],
		},
		{
			artifactType: "ISSUE",
			supportedAutomatedAssessmentModes: ["LANGUAGE_MODEL"],
			recommendedRequirements: mockIssueEvidence,
			allowedSources: [
				{
					sourceKind: "scm.issue.core",
					displayName: "Issue details",
					description: "Issue metadata and rendered description.",
					privacyClass: "PERSONAL",
					supportsComplete: true,
					supportsCurrent: true,
					supportsEmpty: false,
					authorizedForAutomatedAssessment: true,
				},
				{
					sourceKind: "scm.issue.comments",
					displayName: "Issue comments",
					description: "Issue discussion comments.",
					privacyClass: "PERSONAL",
					supportsComplete: true,
					supportsCurrent: false,
					supportsEmpty: true,
					authorizedForAutomatedAssessment: true,
				},
			],
		},
		{
			artifactType: "CONVERSATION_THREAD",
			supportedAutomatedAssessmentModes: ["LANGUAGE_MODEL"],
			recommendedRequirements: mockConversationEvidence,
			allowedSources: [
				{
					sourceKind: "slack.conversation.thread",
					displayName: "Slack thread",
					description: "Ordered human messages from one Slack thread.",
					privacyClass: "PERSONAL",
					supportsComplete: true,
					supportsCurrent: true,
					supportsEmpty: false,
					authorizedForAutomatedAssessment: false,
				},
			],
		},
	],
} satisfies PracticeEvidenceOptions;
