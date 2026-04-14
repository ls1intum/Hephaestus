import type { Meta, StoryObj } from "@storybook/react";
import { AdminAgentsPage } from "./AdminAgentsPage";

const baseConfigs = [
	{
		id: 1,
		name: "claude-primary",
		enabled: true,
		agentType: "CLAUDE_CODE",
		modelName: "claude-sonnet-4-20250514",
		modelVersion: "2026-03-17",
		llmProvider: "ANTHROPIC",
		hasLlmApiKey: false,
		timeoutSeconds: 600,
		maxConcurrentJobs: 2,
		allowInternet: false,
		credentialMode: "PROXY",
		createdAt: new Date("2026-04-10T08:00:00Z"),
		updatedAt: new Date("2026-04-12T10:00:00Z"),
	},
	{
		id: 2,
		name: "pi-openai-direct",
		enabled: true,
		agentType: "PI",
		modelName: "gpt-5.4-mini",
		modelVersion: "2026-03-17",
		llmProvider: "OPENAI",
		hasLlmApiKey: true,
		timeoutSeconds: 900,
		maxConcurrentJobs: 3,
		allowInternet: true,
		credentialMode: "API_KEY",
		createdAt: new Date("2026-04-11T09:30:00Z"),
		updatedAt: new Date("2026-04-12T10:45:00Z"),
	},
] as const;

const baseJobs = {
	content: [
		{
			id: "job-1",
			jobType: "PULL_REQUEST_REVIEW",
			status: "COMPLETED",
			metadata: { pullRequestId: 42 },
			output: { summary: "Delivered" },
			configSnapshot: { configName: "claude-primary", agentType: "CLAUDE_CODE" },
			configName: "claude-primary",
			configAgentType: "CLAUDE_CODE",
			configLlmProvider: "ANTHROPIC",
			configModelName: "claude-sonnet-4-20250514",
			configModelVersion: "2026-03-17",
			createdAt: new Date("2026-04-12T11:00:00Z"),
			startedAt: new Date("2026-04-12T11:01:00Z"),
			completedAt: new Date("2026-04-12T11:03:00Z"),
			retryCount: 0,
			deliveryStatus: "DELIVERED",
			llmModel: "claude-sonnet-4-20250514",
			llmModelVersion: "2026-03-17",
			llmTotalCalls: 8,
			llmTotalInputTokens: 14500,
			llmTotalOutputTokens: 3100,
			llmTotalReasoningTokens: 1200,
			llmCacheReadTokens: 800,
			llmCacheWriteTokens: 150,
			llmCostUsd: 0.37,
		},
		{
			id: "job-2",
			jobType: "PULL_REQUEST_REVIEW",
			status: "FAILED",
			metadata: { pullRequestId: 43 },
			output: { error: "Delivery failed" },
			configSnapshot: { configName: "pi-openai-direct", agentType: "PI" },
			configName: "pi-openai-direct",
			configAgentType: "PI",
			configLlmProvider: "OPENAI",
			configModelName: "gpt-5.4-mini",
			configModelVersion: "2026-03-17",
			createdAt: new Date("2026-04-12T12:00:00Z"),
			startedAt: new Date("2026-04-12T12:01:00Z"),
			completedAt: new Date("2026-04-12T12:04:00Z"),
			retryCount: 1,
			deliveryStatus: "FAILED",
			errorMessage: "Git provider rejected the comment",
			llmModel: "gpt-5.4-mini",
			llmModelVersion: "2026-03-17",
			llmTotalCalls: 6,
			llmTotalInputTokens: 11300,
			llmTotalOutputTokens: 2100,
			llmTotalReasoningTokens: 600,
			llmCacheReadTokens: 300,
			llmCacheWriteTokens: 50,
			llmCostUsd: 0.18,
		},
	],
	empty: false,
	first: true,
	last: true,
	number: 0,
	numberOfElements: 2,
	size: 10,
	totalElements: 2,
	totalPages: 1,
} as const;

const meta = {
	component: AdminAgentsPage,
	parameters: {
		layout: "fullscreen",
	},
	args: {
		workspaceSlug: "demo-workspace",
		configs: [...baseConfigs],
		jobsPage: { ...baseJobs, content: [...baseJobs.content] },
		selectedJob: undefined,
		selectedJobId: null,
		jobsFilter: { status: "ALL", configId: "", page: 0, size: 10 },
		isLoadingConfigs: false,
		isLoadingJobs: false,
		isLoadingJobDetails: false,
		configsError: null,
		jobsError: null,
		jobDetailsError: null,
		isSavingConfig: false,
		deletingConfigId: null,
		cancellingJobId: null,
		retryingJobId: null,
		onRefresh: async () => {},
		onCreateConfig: async () => {},
		onUpdateConfig: async () => {},
		onDeleteConfig: async () => {},
		onChangeJobsFilter: () => {},
		onSelectJob: () => {},
		onCancelJob: async () => {},
		onRetryDelivery: async () => {},
	},
	tags: ["autodocs"],
	title: "Admin/Agents/AdminAgentsPage",
} satisfies Meta<typeof AdminAgentsPage>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Loading: Story = {
	args: {
		isLoadingConfigs: true,
		isLoadingJobs: true,
	},
};

export const Empty: Story = {
	args: {
		configs: [],
		jobsPage: {
			content: [],
			empty: true,
			first: true,
			last: true,
			number: 0,
			numberOfElements: 0,
			size: 10,
			totalElements: 0,
			totalPages: 0,
		},
	},
};

export const WithJobDetails: Story = {
	args: {
		selectedJobId: "job-2",
		selectedJob: baseJobs.content[1],
	},
};

export const ErrorState: Story = {
	args: {
		configsError: new Error("Server rejected the config request."),
		jobsError: new Error("Could not fetch job history."),
	},
};
