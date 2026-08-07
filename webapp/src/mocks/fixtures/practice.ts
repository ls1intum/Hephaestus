import type {
	PracticeAutomatedReviewPolicy,
	PracticeAutomatedReviewValidation,
	PracticeBinding,
	PracticeDefinitionOptions,
} from "@/api/types.gen";

export const mockAuthorDeclaredEvidenceValidation = {
	status: "AUTHOR_DECLARED",
	sourceContractVersion: "1.0.0",
	policyDigest: "0".repeat(64),
	reviewRuleFingerprint: `v2:${"0".repeat(64)}`,
} satisfies PracticeAutomatedReviewValidation;

export const mockPullRequestPolicy = {
	sourceContractVersion: "1.0.0",
	automatedReview: {
		mode: "LANGUAGE_MODEL",
		evidenceSufficiency: "SUFFICIENT_WHEN_REQUIREMENTS_MET",
	},
	whenEvidenceIsInsufficient: "SKIP_AUTOMATED_REVIEW",
	knownLimitations: [
		{
			code: "RUNTIME_BEHAVIOR_NOT_OBSERVED",
			description: "Repository evidence does not establish behavior in a deployed runtime.",
		},
	],
} satisfies PracticeAutomatedReviewPolicy;

const mockIssuePolicy = {
	sourceContractVersion: "1.0.0",
	automatedReview: {
		mode: "LANGUAGE_MODEL",
		evidenceSufficiency: "SUFFICIENT_WHEN_REQUIREMENTS_MET",
	},
	whenEvidenceIsInsufficient: "SKIP_AUTOMATED_REVIEW",
	knownLimitations: [
		{
			code: "IMPLEMENTATION_NOT_OBSERVED",
			description:
				"Issue evidence does not establish whether the described work was implemented correctly.",
		},
	],
} satisfies PracticeAutomatedReviewPolicy;

const mockConversationPolicy = {
	sourceContractVersion: "1.0.0",
	automatedReview: {
		mode: "LANGUAGE_MODEL",
		evidenceSufficiency: "SUFFICIENT_WHEN_REQUIREMENTS_MET",
	},
	whenEvidenceIsInsufficient: "SKIP_AUTOMATED_REVIEW",
	knownLimitations: [
		{
			code: "PRIVATE_CONTEXT_NOT_OBSERVED",
			description:
				"The captured thread does not include decisions or context shared outside the conversation.",
		},
	],
} satisfies PracticeAutomatedReviewPolicy;

/** What a pull-request practice reviews when the author accepts every recommendation. */
export const mockPullRequestBinding = {
	signals: ["scm.pull_request.opened", "scm.pull_request.ready", "scm.pull_request.synchronized"],
	needs: [
		{ sourceKind: "scm.pull-request.comments", stance: "REQUIRED" },
		{ sourceKind: "scm.pull-request.core", stance: "REQUIRED" },
		{ sourceKind: "scm.pull-request.diff", stance: "REQUIRED" },
	],
} satisfies PracticeBinding;

/**
 * The occasion the refactor exists for: the same practice, reviewed again at the merge, reading the
 * review threads exhaustively so it may say nobody resolved one.
 */
export const mockMergeBinding = {
	signals: ["scm.pull_request.merged"],
	needs: [
		{ sourceKind: "scm.pull-request.core", stance: "REQUIRED" },
		{ sourceKind: "scm.review-threads", stance: "EXHAUSTIVE" },
	],
} satisfies PracticeBinding;

export const mockIssueBinding = {
	signals: ["scm.issue.labeled", "scm.issue.opened"],
	needs: [
		{ sourceKind: "scm.issue.comments", stance: "REQUIRED" },
		{ sourceKind: "scm.issue.core", stance: "REQUIRED" },
	],
} satisfies PracticeBinding;

export const mockConversationBinding = {
	signals: ["chat.conversation_thread.settled"],
	needs: [{ sourceKind: "slack.conversation.thread", stance: "REQUIRED" }],
} satisfies PracticeBinding;

export const mockPracticeDefinitionOptions = {
	workTypes: [
		{
			artifactKind: "scm.pull_request",
			signals: [
				{ signal: "scm.pull_request.opened", displayName: "Opened", recommended: true },
				{
					signal: "scm.pull_request.ready",
					displayName: "Marked ready for review",
					recommended: true,
				},
				{
					signal: "scm.pull_request.synchronized",
					displayName: "New commits pushed",
					recommended: true,
				},
				{
					signal: "scm.pull_request.reviewed",
					displayName: "Review submitted",
					recommended: false,
				},
				{ signal: "scm.pull_request.merged", displayName: "Merged", recommended: false },
				{
					signal: "scm.pull_request.closed",
					displayName: "Closed without merging",
					recommended: false,
				},
				{
					signal: "scm.pull_request.review_requested",
					displayName: "Review requested by hand",
					recommended: false,
				},
			],
			supportedAutomatedReviewModes: ["LANGUAGE_MODEL"],
			recommendedPolicy: mockPullRequestPolicy,
			recommendedNeeds: mockPullRequestBinding.needs,
			allowedSources: [
				{
					sourceKind: "scm.pull-request.core",
					displayName: "Pull request details",
					description: "Pull request metadata and commit subjects for the reviewed pull request.",
					privacyClass: "PERSONAL",
					requiredQuality: "COMPLETE",
					supportsExhaustiveEvidence: true,
				},
				{
					sourceKind: "scm.pull-request.diff",
					displayName: "Code changes",
					description: "Code changes in the reviewed pull request.",
					privacyClass: "PERSONAL",
					requiredQuality: "COMPLETE_AND_NON_EMPTY",
					supportsExhaustiveEvidence: true,
				},
				{
					sourceKind: "scm.pull-request.comments",
					displayName: "Inline review comments",
					description: "Inline review comments mirrored by the application.",
					privacyClass: "PERSONAL",
					requiredQuality: "ANY_CAPTURE",
					supportsExhaustiveEvidence: true,
				},
				{
					sourceKind: "scm.review-threads",
					displayName: "Review threads",
					description: "Review conversations and whether each one was resolved.",
					privacyClass: "PERSONAL",
					requiredQuality: "ANY_CAPTURE",
					supportsExhaustiveEvidence: true,
				},
				{
					sourceKind: "scm.linked-work-items",
					displayName: "Linked issues",
					description: "Issues this pull request references.",
					privacyClass: "PERSONAL",
					requiredQuality: "ANY_CAPTURE",
					supportsExhaustiveEvidence: false,
				},
			],
		},
		{
			artifactKind: "scm.issue",
			signals: [
				{ signal: "scm.issue.opened", displayName: "Opened", recommended: true },
				{ signal: "scm.issue.labeled", displayName: "Labeled", recommended: true },
				{ signal: "scm.issue.closed", displayName: "Closed", recommended: false },
				{
					signal: "scm.issue.review_requested",
					displayName: "Review requested by hand",
					recommended: false,
				},
			],
			supportedAutomatedReviewModes: ["LANGUAGE_MODEL"],
			recommendedPolicy: mockIssuePolicy,
			recommendedNeeds: mockIssueBinding.needs,
			allowedSources: [
				{
					sourceKind: "scm.issue.core",
					displayName: "Issue details",
					description: "Issue metadata and rendered description.",
					privacyClass: "PERSONAL",
					requiredQuality: "COMPLETE",
					supportsExhaustiveEvidence: true,
				},
				{
					sourceKind: "scm.issue.comments",
					displayName: "Issue comments",
					description: "Issue discussion comments.",
					privacyClass: "PERSONAL",
					requiredQuality: "ANY_CAPTURE",
					supportsExhaustiveEvidence: true,
				},
			],
		},
		{
			artifactKind: "chat.conversation_thread",
			signals: [
				{
					signal: "chat.conversation_thread.settled",
					displayName: "Discussion settled",
					recommended: true,
				},
			],
			supportedAutomatedReviewModes: ["LANGUAGE_MODEL"],
			recommendedPolicy: mockConversationPolicy,
			recommendedNeeds: mockConversationBinding.needs,
			allowedSources: [
				{
					sourceKind: "slack.conversation.thread",
					displayName: "Slack thread",
					description: "Ordered human messages from one Slack thread.",
					privacyClass: "PERSONAL",
					requiredQuality: "COMPLETE",
					supportsExhaustiveEvidence: true,
				},
			],
		},
	],
} satisfies PracticeDefinitionOptions;
