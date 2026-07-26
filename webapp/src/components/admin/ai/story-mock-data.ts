import type { AgentJob, AvailableLlmModel, PracticeReviewSettings } from "@/api/types.gen";

export const mockAvailableModels: AvailableLlmModel[] = [
	{
		id: 1,
		scope: "SHARED",
		displayName: "GPT-5",
		connectionDisplayName: "OpenAI production",
		pricingMode: "PRICED",
		per1mInputUsd: 3,
		per1mOutputUsd: 15,
		supportsReasoning: true,
	},
	{
		id: 2,
		scope: "SHARED",
		displayName: "Local Llama (self-hosted)",
		connectionDisplayName: "On-prem GPU",
		pricingMode: "NO_CHARGE",
		supportsReasoning: false,
	},
	{
		id: 10,
		scope: "WORKSPACE",
		displayName: "My OpenAI key",
		connectionDisplayName: "My provider",
		pricingMode: "UNPRICED",
		supportsReasoning: true,
	},
];

export const mockPracticeReviewSettings: PracticeReviewSettings = {
	cooldownMinutes: 30,
	deliverToMerged: false,
	runForAllUsers: true,
	skipDrafts: true,
	// Mix of explicit overrides (Reset to default) and inherited / undefined (Inherited from default).
	skipDraftsOverride: true,
	cooldownMinutesOverride: 30,
	deliverToMergedOverride: undefined,
	runForAllUsersOverride: undefined,
};

export const mockJobCompleted: AgentJob = {
	id: "job-completed-1",
	jobType: "PULL_REQUEST_REVIEW",
	status: "COMPLETED",
	model: "gpt-5.4-mini",
	configSnapshot: { name: "Default reviewer", llmProvider: "OPENAI" },
	createdAt: new Date("2026-05-20T10:00:00Z"),
	completedAt: new Date("2026-05-20T10:05:00Z"),
	deliveryStatus: "DELIVERED",
	llmModel: "openai/gpt-oss-120b",
	llmTotalInputTokens: 24_000,
	llmTotalOutputTokens: 914,
	llmTotalReasoningTokens: 120,
	llmTotalCalls: 7,
	llmCostUsd: 0.116,
	retryCount: 0,
	exitCode: 0,
};

export const mockJobRunning: AgentJob = {
	id: "job-running-1",
	jobType: "PULL_REQUEST_REVIEW",
	status: "RUNNING",
	model: "openai/gpt-oss-120b",
	configSnapshot: { name: "GPU gateway (OpenAI)", llmProvider: "OPENAI" },
	createdAt: new Date("2026-05-20T11:58:00Z"),
	retryCount: 0,
};

export const mockJobFailedDelivery: AgentJob = {
	id: "job-failed-delivery-1",
	jobType: "PULL_REQUEST_REVIEW",
	status: "COMPLETED",
	model: "gpt-5.4-mini",
	configSnapshot: { name: "Default reviewer" },
	createdAt: new Date("2026-05-20T09:00:00Z"),
	completedAt: new Date("2026-05-20T09:05:00Z"),
	deliveryStatus: "FAILED",
	errorMessage: "GitLab API returned 403 when posting the MR note.",
	llmModel: "openai/gpt-oss-120b",
	llmTotalInputTokens: 31_000,
	llmTotalOutputTokens: 1_200,
	llmCostUsd: 0.18,
	retryCount: 1,
	exitCode: 0,
};

export const mockJobQueued: AgentJob = {
	id: "job-queued-1",
	jobType: "PULL_REQUEST_REVIEW",
	status: "QUEUED",
	model: "gpt-5.4-mini",
	configSnapshot: { name: "Default reviewer" },
	createdAt: new Date("2026-05-20T12:00:00Z"),
	retryCount: 0,
};

export const mockJobTimedOut: AgentJob = {
	id: "job-timed-out-1",
	jobType: "PULL_REQUEST_REVIEW",
	status: "TIMED_OUT",
	model: "openai/gpt-oss-120b",
	configSnapshot: { name: "GPU gateway (OpenAI)", llmProvider: "OPENAI" },
	createdAt: new Date("2026-05-20T08:00:00Z"),
	completedAt: new Date("2026-05-20T08:20:00Z"),
	errorMessage: "Agent exceeded the 1200s timeout and was terminated.",
	llmModel: "gpt-oss-120b",
	retryCount: 1,
	exitCode: 124,
};

export const mockJobs: AgentJob[] = [
	mockJobCompleted,
	mockJobRunning,
	mockJobQueued,
	mockJobFailedDelivery,
	mockJobTimedOut,
];
