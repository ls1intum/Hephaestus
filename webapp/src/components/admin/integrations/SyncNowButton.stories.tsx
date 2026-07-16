import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "storybook/test";
import type { SyncJob } from "@/api/types.gen";
import { SyncNowButton } from "./SyncNowButton";

const runningJob: SyncJob = {
	id: 1,
	type: "RECONCILIATION",
	trigger: "MANUAL",
	status: "RUNNING",
	cancelRequested: false,
	createdAt: new Date("2026-07-14T10:00:00Z"),
};

/**
 * The manual-sync trigger. Disabled while a job is already running or a trigger is in flight, so a
 * double-click can't enqueue a second run. It owns a visually-hidden `aria-live` region because a
 * plain button-label swap is not reliably announced — the region stays empty until a run begins.
 *
 * The two pending inputs are deliberately separate. `isTriggering` means *this* button's operation is
 * starting, and is what earns the spinner and the "Starting…" label; `isBusy` means someone else's is,
 * and only earns the disable. A card that drives Sync and Backfill from one mutation must decide which
 * is which before rendering — passing the same flag to both makes each button claim the other's work.
 */
const meta = {
	component: SyncNowButton,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: { onClick: fn() },
	argTypes: {
		label: { control: "text", description: "Button label while idle (e.g. 'Backfill')." },
		isTriggering: { control: "boolean", description: "*This* button's trigger is in flight." },
		isBusy: { control: "boolean", description: "A different operation is in flight." },
	},
} satisfies Meta<typeof SyncNowButton>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Idle — clickable, and the live region is silent until a run actually starts. */
export const Idle: Story = {
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.queryByText("Starting sync")).not.toBeInTheDocument();
		await expect(canvas.queryByText("Sync in progress")).not.toBeInTheDocument();
		await userEvent.click(canvas.getByRole("button", { name: /sync now/i }));
		await expect(args.onClick).toHaveBeenCalledTimes(1);
	},
};

/** Trigger in flight — the button is disabled and the live region announces the start. */
export const Triggering: Story = {
	args: { isTriggering: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /starting/i })).toBeDisabled();
		await expect(canvas.getByText("Starting sync")).toBeInTheDocument();
	},
};

/**
 * A job is already running — disabled and labelled "Syncing…". The announcement names the job the
 * server is actually running rather than this button's own operation: every trigger on a card watches
 * the same job, so a Backfill button announcing "backfill in progress" during a reconciliation would
 * be reporting the wrong run.
 */
export const ActiveJob: Story = {
	args: { activeJob: runningJob },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /syncing/i })).toBeDisabled();
		await expect(canvas.getByText("Reconciliation in progress")).toBeInTheDocument();
	},
};

/**
 * A *different* trigger on the same card is starting. The server takes one job at a time, so this
 * button is genuinely unavailable — but it stays silent about it: idle label, idle icon, and nothing
 * announced. This is the state that keeps "Sync now" from impersonating a backfill.
 */
export const BusyWithAnotherOperation: Story = {
	args: { isBusy: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const button = canvas.getByRole("button", { name: /sync now/i });
		await expect(button).toBeDisabled();
		await expect(button).toHaveTextContent(/^Sync now$/);
		await expect(canvas.queryByText(/starting/i)).not.toBeInTheDocument();
		await expect(canvas.queryByText(/in progress/i)).not.toBeInTheDocument();
	},
};

/** The Backfill trigger names its own operation when it starts, not the generic "sync". */
export const BackfillTriggering: Story = {
	args: { label: "Backfill", operationLabel: "backfill", isTriggering: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /starting/i })).toBeDisabled();
		await expect(canvas.getByText("Starting backfill")).toBeInTheDocument();
	},
};
