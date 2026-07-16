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
 * plain button-label swap is not reliably announced — the region stays empty until a run begins,
 * then speaks "Starting sync" / "Sync in progress".
 */
const meta = {
	component: SyncNowButton,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: { onClick: fn() },
	argTypes: {
		label: { control: "text", description: "Button label while idle (e.g. 'Backfill')." },
		isTriggering: { control: "boolean", description: "A trigger request is in flight." },
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

/** A job is already running — disabled, labelled "Syncing…", and the region announces progress. */
export const ActiveJob: Story = {
	args: { activeJob: runningJob },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /syncing/i })).toBeDisabled();
		await expect(canvas.getByText("Sync in progress")).toBeInTheDocument();
	},
};
