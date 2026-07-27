import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { expectControlOnScreen, expectDialogFitsViewport, expectPageReflows } from "@/test/reflow";
import { AgentJobDetailsPanel } from "./AgentJobDetailsPanel";
import {
	mockJobCompleted,
	mockJobFailedDelivery,
	mockJobRunning,
	mockJobTimedOut,
} from "./story-mock-data";

/**
 * Slide-over panel with a run's overview, usage, error, and config snapshot, plus confirm dialogs
 * for cancelling a running job or retrying a failed delivery.
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

export const Completed: Story = {};

export const Running: Story = {
	args: { job: mockJobRunning },
	// The Sheet and its AlertDialog render in portals, so every query here goes through `screen`.
	play: async () => {
		await userEvent.click(screen.getByRole("button", { name: /^cancel run$/i }));
		const dialog = await screen.findByRole("alertdialog");
		await expect(within(dialog).getByText(/the running container stops/i)).toBeInTheDocument();
	},
};

export const TimedOut: Story = {
	args: { job: mockJobTimedOut },
};

export const FailedDelivery: Story = {
	args: { job: mockJobFailedDelivery },
};

/**
 * WCAG 2.2 SC 1.4.10 at 320 px. `SheetContent`'s `data-[side=right]:w-3/4` outranks a plain
 * `w-full`, so a width override here has to be attribute-qualified too or the panel renders at 75 %
 * of a phone.
 */
export const MobileReflow: Story = {
	args: { job: mockJobRunning },
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 375, 768] },
	},
	play: async () => {
		await expectPageReflows();
		const panel = await screen.findByRole("dialog");
		await expect(panel.getBoundingClientRect().width).toBeGreaterThanOrEqual(window.innerWidth - 1);

		await userEvent.click(screen.getByRole("button", { name: /^cancel run$/i }));
		await screen.findByRole("alertdialog");
		await expectDialogFitsViewport();
		await expectControlOnScreen(screen.getByRole("button", { name: /keep running/i }));
	},
};
