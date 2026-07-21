import type { AgentConfig, AgentJob, AiSettingsView, AvailableLlmModel } from "@/api/types.gen";

export const mockConfigProxy: AgentConfig = {
	id: 1,
	name: "Default reviewer",
	llmProvider: "ANTHROPIC",
	modelName: "claude-sonnet-4-5",
	allowInternet: false,
	enabled: true,
	hasLlmApiKey: false,
	maxConcurrentJobs: 2,
	timeoutSeconds: 600,
	createdAt: new Date("2026-04-01T10:00:00Z"),
	updatedAt: new Date("2026-05-01T10:00:00Z"),
};

export const mockConfigApiKey: AgentConfig = {
	id: 2,
	name: "GPU gateway (OpenAI)",
	llmProvider: "OPENAI",
	modelName: "gpt-oss-120b",
	llmBaseUrl: "https://gpu.example.edu/api",
	allowInternet: true,
	enabled: true,
	hasLlmApiKey: true,
	maxConcurrentJobs: 1,
	timeoutSeconds: 1200,
	createdAt: new Date("2026-04-10T10:00:00Z"),
};

export const mockConfigDisabled: AgentConfig = {
	id: 3,
	name: "Legacy model",
	llmProvider: "AZURE_OPENAI",
	modelName: "gpt-4o",
	allowInternet: true,
	enabled: false,
	hasLlmApiKey: true,
	maxConcurrentJobs: 1,
	timeoutSeconds: 300,
	createdAt: new Date("2026-03-01T10:00:00Z"),
};

export const mockConfigBoundShared: AgentConfig = {
	id: 4,
	name: "Shared GPT-5",
	llmProvider: "OPENAI",
	allowInternet: false,
	enabled: true,
	hasLlmApiKey: false,
	instanceModelId: 1,
	maxConcurrentJobs: 2,
	timeoutSeconds: 600,
	createdAt: new Date("2026-06-01T10:00:00Z"),
};

export const mockConfigBoundOwn: AgentConfig = {
	id: 5,
	name: "My own key",
	llmProvider: "OPENAI",
	allowInternet: true,
	enabled: true,
	hasLlmApiKey: false,
	workspaceModelId: 10,
	maxConcurrentJobs: 1,
	timeoutSeconds: 900,
	createdAt: new Date("2026-06-01T10:00:00Z"),
};

export const mockConfigs: AgentConfig[] = [mockConfigProxy, mockConfigApiKey, mockConfigDisabled];

export const mockAvailableModels: AvailableLlmModel[] = [
	{
		id: 1,
		scope: "SHARED",
		displayName: "GPT-5 (Azure, EU)",
		connectionDisplayName: "Azure EU",
		modality: "CHAT",
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
		modality: "CHAT",
		pricingMode: "FREE",
		supportsReasoning: false,
	},
	{
		id: 10,
		scope: "WORKSPACE",
		displayName: "My OpenAI key",
		connectionDisplayName: "My provider",
		modality: "CHAT",
		pricingMode: "UNPRICED",
		supportsReasoning: true,
	},
];

export const mockAiSettings: AiSettingsView = {
	practicesEnabled: true,
	mentorEnabled: true,
	practiceConfigId: 1,
	mentorConfigId: 2,
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
	configId: 1,
	configName: "Default reviewer",
	configSnapshot: { name: "Default reviewer", llmProvider: "ANTHROPIC" },
	createdAt: new Date("2026-05-20T10:00:00Z"),
	completedAt: new Date("2026-05-20T10:05:00Z"),
	deliveryStatus: "DELIVERED",
	llmModel: "claude-sonnet-4-5",
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
	configId: 2,
	configName: "GPU gateway (OpenAI)",
	configSnapshot: { name: "GPU gateway (OpenAI)", llmProvider: "OPENAI" },
	createdAt: new Date("2026-05-20T11:58:00Z"),
	retryCount: 0,
};

export const mockJobFailedDelivery: AgentJob = {
	id: "job-failed-delivery-1",
	jobType: "PULL_REQUEST_REVIEW",
	status: "COMPLETED",
	configId: 1,
	configName: "Default reviewer",
	configSnapshot: { name: "Default reviewer" },
	createdAt: new Date("2026-05-20T09:00:00Z"),
	completedAt: new Date("2026-05-20T09:05:00Z"),
	deliveryStatus: "FAILED",
	errorMessage: "GitLab API returned 403 when posting the MR note.",
	llmModel: "claude-sonnet-4-5",
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
	configId: 1,
	configName: "Default reviewer",
	configSnapshot: { name: "Default reviewer" },
	createdAt: new Date("2026-05-20T12:00:00Z"),
	retryCount: 0,
};

export const mockJobTimedOut: AgentJob = {
	id: "job-timed-out-1",
	jobType: "PULL_REQUEST_REVIEW",
	status: "TIMED_OUT",
	configId: 2,
	configName: "GPU gateway (OpenAI)",
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
