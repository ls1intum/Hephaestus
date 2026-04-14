import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { AgentJobsTable } from "./AgentJobsTable";
import { mockAgentConfigs, mockAgentJobs, mockAgentJobsPage } from "./storyMockData";

/**
 * Paginated admin table for recent practice-review jobs.
 */
const meta = {
	component: AgentJobsTable,
	tags: ["autodocs"],
	args: {
		configs: mockAgentConfigs,
		jobsPage: mockAgentJobsPage,
		selectedJobId: null,
		jobsFilter: { status: "ALL", configId: "", page: 0, size: 10 },
		isLoading: false,
		error: null,
		cancellingJobId: null,
		retryingJobId: null,
		onRefresh: fn(),
		onChangeJobsFilter: fn(),
		onSelectJob: fn(),
		onRequestCancelJob: fn(),
		onRetryDelivery: fn().mockResolvedValue(undefined),
	},
} satisfies Meta<typeof AgentJobsTable>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const SelectedRow: Story = {
	args: {
		selectedJobId: mockAgentJobs[1].id,
	},
};

export const Loading: Story = {
	args: {
		isLoading: true,
	},
};

export const Empty: Story = {
	args: {
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
