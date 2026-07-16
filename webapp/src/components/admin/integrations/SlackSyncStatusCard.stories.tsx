import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "storybook/test";
import type { ConnectionSyncStatus, SyncJob } from "@/api/types.gen";
import { SlackSyncStatusCard } from "./SlackSyncStatusCard";

const baseStatus: ConnectionSyncStatus = {
	connectionId: 8,
	connectionState: "ACTIVE",
	kind: "SLACK",
	health: "HEALTHY",
	resourceCounts: { total: 3, errored: 0, pending: 0, stale: 0 },
	// SlackIntegrationSyncRunner does not override supportsBackfill — Slack reconciles only.
	backfillSupported: false,
	lastSuccessfulSyncAt: new Date("2026-07-14T10:00:00Z"),
};

const runningJob: SyncJob = {
	id: 9,
	type: "RECONCILIATION",
	trigger: "MANUAL",
	status: "RUNNING",
	cancelRequested: false,
	createdAt: new Date("2026-07-15T10:00:00Z"),
	startedAt: new Date("2026-07-15T10:00:05Z"),
	itemsProcessed: 2,
	itemsTotal: 3,
};

/**
 * Sync status for the Slack integration. While the connection is active it offers a manual sync
 * and — when a job is running — a cooperative Cancel that flips to "Stopping after current step…"
 * once requested. Distinguishes "No channels activated yet" (active, nothing to sync) from a plain
 * "Never synced" (inactive/suspended) so the empty state reads truthfully.
 *
 * A running job shows determinate progress whenever the server knows the total. Slack was previously
 * the one integration without it — a run rendered a progress bar on the *overview* card and nothing on
 * the Slack detail page, which is the page an admin opens precisely to watch the run.
 */
const meta = {
	component: SlackSyncStatusCard,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		status: baseStatus,
		isConnectionActive: true,
		isTriggering: false,
		isCancelling: false,
		onSync: fn(),
		onCancel: fn(),
	},
} satisfies Meta<typeof SlackSyncStatusCard>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Active connection with a prior successful sync — the steady state. */
export const Connected: Story = {};

/** Active but nothing activated yet — distinct copy from "Never synced". */
export const NoChannelsActivatedYet: Story = {
	args: { status: { ...baseStatus, lastSuccessfulSyncAt: undefined } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/no channels activated yet/i)).toBeInTheDocument();
	},
};

/** A job is running — Sync is disabled, progress is shown, and a Cancel control appears. */
export const ActiveJobWithCancel: Story = {
	args: { status: { ...baseStatus, activeJob: runningJob } },
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /syncing/i })).toBeDisabled();

		// The detail page must show the run it exists to show.
		await expect(canvas.getByRole("progressbar")).toBeInTheDocument();

		const cancel = canvas.getByRole("button", { name: /^cancel$/i });
		await expect(cancel).toBeEnabled();
		await userEvent.click(cancel);
		await expect(args.onCancel).toHaveBeenCalledTimes(1);
	},
};

/**
 * A run whose total the server doesn't know yet. Per the NN/g 10s rule a multi-second job still owes
 * the reader feedback, so it degrades to an indeterminate spinner plus the current step rather than
 * faking a percentage.
 */
export const ActiveJobIndeterminate: Story = {
	args: {
		status: {
			...baseStatus,
			activeJob: {
				...runningJob,
				itemsProcessed: undefined,
				itemsTotal: undefined,
				progress: { currentStep: "Listing channels" },
			},
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/listing channels/i)).toBeInTheDocument();
		await expect(canvas.queryByRole("progressbar")).not.toBeInTheDocument();
	},
};

/** Cancel already requested — the button reads "Stopping…" and is disabled. */
export const Cancelling: Story = {
	args: {
		status: { ...baseStatus, activeJob: { ...runningJob, cancelRequested: true } },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			canvas.getByRole("button", { name: /stopping after current step/i }),
		).toBeDisabled();
	},
};

/** Suspended connection that never synced — no controls, plain "Never synced". */
export const NeverSynced: Story = {
	args: {
		isConnectionActive: false,
		status: {
			...baseStatus,
			connectionState: "SUSPENDED",
			health: "SUSPENDED",
			lastSuccessfulSyncAt: undefined,
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/never synced/i)).toBeInTheDocument();
		await expect(canvas.queryByRole("button", { name: /sync now/i })).not.toBeInTheDocument();
	},
};

/**
 * Suspended after a successful history — the complement of {@link NeverSynced}. The last-synced
 * timestamp still reads truthfully (the collected data did not disappear), but no control is
 * offered while the connection is not active.
 */
export const Suspended: Story = {
	args: {
		isConnectionActive: false,
		status: { ...baseStatus, connectionState: "SUSPENDED", health: "SUSPENDED" },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/last synced/i)).toBeInTheDocument();
		await expect(canvas.queryByText(/never synced/i)).not.toBeInTheDocument();
		await expect(canvas.queryByRole("button", { name: /sync now/i })).not.toBeInTheDocument();
		await expect(canvas.queryByRole("button", { name: /^cancel$/i })).not.toBeInTheDocument();
	},
};
