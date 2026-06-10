import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { AgentJobDetailsPanel } from "./AgentJobDetailsPanel";
import {
	mockJobCompleted,
	mockJobFailedDelivery,
	mockJobRunning,
	mockJobTimedOut,
} from "./storyMockData";

/**
 * Slide-over panel with a job's overview, usage, error, and config snapshot, plus
 * confirm dialogs for cancelling a running job or retrying a failed delivery.
 */
const meta = {
	component: AgentJobDetailsPanel,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
	args: {
		open: true,
		job: mockJobCompleted,
		isCancelling: false,
		isRetrying: false,
		onOpenChange: fn(),
		onCancel: fn(),
		onRetryDelivery: fn(),
	},
} satisfies Meta<typeof AgentJobDetailsPanel>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Completed + delivered job with full usage. */
export const Completed: Story = {};

/** Running job — exposes the Cancel action. */
export const Running: Story = {
	args: { job: mockJobRunning },
	play: async () => {
		// The Sheet + AlertDialog render in portals — query the whole document.
		await userEvent.click(screen.getByRole("button", { name: /^cancel job$/i }));
		const dialog = await screen.findByRole("alertdialog");
		await expect(
			within(dialog).getByText(/the running container will be stopped/i),
		).toBeInTheDocument();
	},
};

/** Timed-out job — error message + exit code surfaced. */
export const TimedOut: Story = {
	args: { job: mockJobTimedOut },
};

/** Completed but delivery failed — exposes Retry delivery + error message. */
export const FailedDelivery: Story = {
	args: { job: mockJobFailedDelivery },
};
