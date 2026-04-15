import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { AgentJobDetailsPanel } from "./AgentJobDetailsPanel";
import { mockAgentJobs } from "./storyMockData";

/**
 * Inline details panel for inspecting a frozen review-agent job snapshot, usage, and delivery state.
 */
const meta = {
	component: AgentJobDetailsPanel,
	tags: ["autodocs"],
	args: {
		job: mockAgentJobs[0],
		isLoading: false,
		error: null,
		cancellingJobId: null,
		retryingJobId: null,
		onClose: fn(),
		onRequestCancelJob: fn(),
		onRetryDelivery: fn().mockResolvedValue(undefined),
	},
} satisfies Meta<typeof AgentJobDetailsPanel>;

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
