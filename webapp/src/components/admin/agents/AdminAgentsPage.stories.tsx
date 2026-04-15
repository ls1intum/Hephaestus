import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { AdminAgentsPage } from "./AdminAgentsPage";
import {
	mockAgentConfigs,
	mockAgentJobs,
	mockAgentJobsPage,
	mockAgentRunners,
} from "./storyMockData";

/**
 * Workspace admin page for managing practice review runtimes and their recent job history.
 */
const meta = {
	component: AdminAgentsPage,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
	args: {
		runners: mockAgentRunners,
		configs: mockAgentConfigs,
		jobsPage: mockAgentJobsPage,
		selectedJob: undefined,
		selectedJobId: null,
		jobsFilter: { status: "ALL", configId: "", page: 0, size: 10 },
		isLoadingRunners: false,
		isLoadingConfigs: false,
		isLoadingJobs: false,
		isLoadingJobDetails: false,
		jobsError: null,
		jobDetailsError: null,
		isSavingRunner: false,
		isSavingConfig: false,
		deletingRunnerId: null,
		deletingConfigId: null,
		cancellingJobId: null,
		retryingJobId: null,
		onRefresh: fn(),
		onCreateRunner: fn().mockResolvedValue(undefined),
		onUpdateRunner: fn().mockResolvedValue(undefined),
		onDeleteRunner: fn().mockResolvedValue(undefined),
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
		isLoadingRunners: true,
		isLoadingConfigs: true,
		isLoadingJobs: true,
	},
};

export const Empty: Story = {
	args: {
		runners: [],
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

export const ConfigSetupBlocked: Story = {
	args: {
		runners: [],
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
		jobsError: new Error("Could not fetch job history."),
		jobDetailsError: new Error("Could not load job details."),
	},
};
