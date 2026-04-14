import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { AgentJobDetailsDialog } from "./AgentJobDetailsDialog";
import { mockAgentJobs } from "./storyMockData";

/**
 * Dialog for inspecting a frozen review-agent job snapshot, usage, and delivery status.
 */
const meta = {
	component: AgentJobDetailsDialog,
	tags: ["autodocs"],
	args: {
		open: true,
		job: mockAgentJobs[0],
		isLoading: false,
		error: null,
		cancellingJobId: null,
		retryingJobId: null,
		onOpenChange: fn(),
		onRequestCancelJob: fn(),
		onRetryDelivery: fn().mockResolvedValue(undefined),
	},
} satisfies Meta<typeof AgentJobDetailsDialog>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Completed: Story = {};

export const FailedDelivery: Story = {
	args: {
		job: mockAgentJobs[1],
	},
};

export const Loading: Story = {
	args: {
		job: undefined,
		isLoading: true,
	},
};
