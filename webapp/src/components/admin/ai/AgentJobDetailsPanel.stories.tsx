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
 * Slide-over panel with a run's overview, usage, error, and config snapshot, plus
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

/** Completed + delivered run with full usage. */
export const Completed: Story = {};

/** Running run — exposes the Cancel action. */
export const Running: Story = {
	args: { job: mockJobRunning },
	play: async () => {
		// The Sheet + AlertDialog render in portals — query the whole document.
		await userEvent.click(screen.getByRole("button", { name: /^cancel run$/i }));
		const dialog = await screen.findByRole("alertdialog");
		await expect(within(dialog).getByText(/the running container stops/i)).toBeInTheDocument();
	},
};

/** Timed-out run — error message + exit code surfaced. */
export const TimedOut: Story = {
	args: { job: mockJobTimedOut },
};

/** Completed but delivery failed — exposes Retry delivery + error message. */
export const FailedDelivery: Story = {
	args: { job: mockJobFailedDelivery },
};

/**
 * The details panel at the WCAG 2.2 SC 1.4.10 reflow width (320 CSS px).
 *
 * `SheetContent` sets its widths as `data-[side=right]:w-3/4` / `data-[side=right]:sm:max-w-sm`,
 * which are attribute-qualified and outrank a plain `w-full` — so the panel was rendering at 75 %
 * of a phone, about 240 px, for label/value rows that need every pixel. This asserts it now really
 * does span the viewport, and that the confirm dialog it opens fits inside it.
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

		// The confirm dialog nested inside the panel is subject to the same bound.
		await userEvent.click(screen.getByRole("button", { name: /^cancel run$/i }));
		await screen.findByRole("alertdialog");
		await expectDialogFitsViewport();
		await expectControlOnScreen(screen.getByRole("button", { name: /keep running/i }));
	},
};
