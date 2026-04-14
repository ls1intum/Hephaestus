import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { AdminAgentsPage } from "./AdminAgentsPage";
import { mockAgentConfigs, mockAgentJobs, mockAgentJobsPage } from "./storyMockData";

/**
 * Workspace admin page for managing practice review runtimes and their recent job history.
 */
const meta = {
	component: AdminAgentsPage,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
	args: {
		configs: mockAgentConfigs,
		jobsPage: mockAgentJobsPage,
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
		onRefresh: fn(),
		onCreateConfig: fn().mockResolvedValue(undefined),
		onUpdateConfig: fn().mockResolvedValue(undefined),
		onDeleteConfig: fn().mockResolvedValue(undefined),
		onChangeJobsFilter: fn(),
		onSelectJob: fn(),
		onCancelJob: fn().mockResolvedValue(undefined),
		onRetryDelivery: fn().mockResolvedValue(undefined),
	},
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

export const JobDetailsOpen: Story = {
	args: {
		selectedJobId: mockAgentJobs[1].id,
		selectedJob: mockAgentJobs[1],
	},
};

export const InlineConfirmations: Story = {
	args: {
		selectedJobId: mockAgentJobs[0].id,
		selectedJob: mockAgentJobs[0],
	},
	render: (args) => <AdminAgentsPage {...args} />,
};

export const ErrorState: Story = {
	args: {
		configsError: new Error("Server rejected the config request."),
		jobsError: new Error("Could not fetch job history."),
	},
};
