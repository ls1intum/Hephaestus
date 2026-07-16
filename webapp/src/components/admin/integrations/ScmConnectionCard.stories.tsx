import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "storybook/test";
import type { ConnectionSyncStatus, SyncJob } from "@/api/types.gen";
import { ScmConnectionCard } from "./ScmConnectionCard";

const baseStatus: ConnectionSyncStatus = {
	connectionId: 7,
	connectionState: "ACTIVE",
	kind: "GITHUB",
	health: "HEALTHY",
	resourceCounts: { total: 12, errored: 0 },
	backfillSupported: true,
	lastSuccessfulSyncAt: new Date("2026-07-14T10:00:00Z"),
	lastEventProcessedAt: new Date("2026-07-15T09:30:00Z"),
	webhookRegistered: true,
	rateLimit: { limit: 5000, remaining: 4200, resetAt: new Date(Date.now() + 45 * 60 * 1000) },
};

const runningJob: SyncJob = {
	id: 12,
	type: "RECONCILIATION",
	trigger: "MANUAL",
	status: "RUNNING",
	cancelRequested: false,
	createdAt: new Date("2026-07-15T10:00:00Z"),
	startedAt: new Date("2026-07-15T10:00:05Z"),
	itemsProcessed: 5,
	itemsTotal: 12,
};

/**
 * The connection plane for a source-control integration: last-sync, webhook activity (or a "Not
 * registered" warning), a rate-limit gauge, an optional backfill rollup, live-job progress and the
 * action row. The Backfill trigger follows the server's own `backfillSupported` capability rather than
 * the vendor name, so it appears for every kind whose runner offers a backfill pass; app installs also
 * get a "Manage installation" link. A running job exposes a cooperative Cancel that flips to
 * "Stopping…" once requested. Load failures show a retryable error instead of a blank card.
 */
const meta = {
	component: ScmConnectionCard,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		label: "GitHub",
		status: baseStatus,
		isLoading: false,
		isConnectionActive: true,
		isAppInstallationWorkspace: false,
		isTriggering: false,
		isCancelling: false,
		onRetry: fn(),
		onSync: fn(),
		onBackfill: fn(),
		onCancel: fn(),
	},
} satisfies Meta<typeof ScmConnectionCard>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Connected + healthy GitHub connection — the steady state. */
export const Connected: Story = {};

/** Status still loading — a skeleton stands in for the metrics. */
export const Loading: Story = { args: { status: undefined, isLoading: true } };

/** No connection row for this workspace — a plain informational line. */
export const Missing: Story = { args: { status: undefined, isConnectionActive: false } };

/** The status query failed — a retryable inline error replaces the metrics. */
export const LoadError: Story = {
	args: { status: undefined, error: new Error("503 Service Unavailable") },
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/couldn't load the github connection/i)).toBeInTheDocument();
		await userEvent.click(canvas.getByRole("button", { name: /retry/i }));
		await expect(args.onRetry).toHaveBeenCalledTimes(1);
	},
};

/** The vendor webhook is not registered — the card flags it instead of implying live delivery. */
export const WebhookNotRegistered: Story = {
	args: { status: { ...baseStatus, webhookRegistered: false } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/not registered/i)).toBeInTheDocument();
	},
};

/** A job is running — Sync/Backfill disable and a cooperative Cancel appears. */
export const ActiveJobRunning: Story = {
	args: { status: { ...baseStatus, activeJob: runningJob } },
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		const cancel = canvas.getByRole("button", { name: /^cancel$/i });
		await expect(cancel).toBeEnabled();
		await userEvent.click(cancel);
		await expect(args.onCancel).toHaveBeenCalledTimes(1);
	},
};

/** Cancel already requested — the button reads "Stopping…" and is disabled to block re-clicks. */
export const Cancelling: Story = {
	args: {
		isCancelling: true,
		status: { ...baseStatus, activeJob: { ...runningJob, cancelRequested: true } },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			canvas.getByRole("button", { name: /stopping after current step/i }),
		).toBeDisabled();
	},
};

/**
 * GitHub with a backfill in flight — the rollup line and the Backfill trigger are shown. `IN_PROGRESS`
 * is one of the four states the provider actually emits (`DISABLED`/`NOT_STARTED`/`IN_PROGRESS`/
 * `COMPLETE`), and it reaches the admin title-cased rather than as a raw token.
 */
export const GitHubWithBackfill: Story = {
	args: {
		status: { ...baseStatus, backfill: { state: "IN_PROGRESS", percent: 40 } },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("In Progress — 40%")).toBeInTheDocument();
		await expect(canvas.queryByText(/IN_PROGRESS/)).not.toBeInTheDocument();
		await expect(canvas.getByRole("button", { name: /backfill/i })).toBeInTheDocument();
	},
};

/** An app-installation workspace gets a link out to manage the install on GitHub. */
export const AppInstallationWorkspace: Story = {
	args: { isAppInstallationWorkspace: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const link = canvas.getByText(/manage installation on github/i).closest("a");
		await expect(link).toHaveAttribute("href", "https://github.com/settings/installations");
	},
};

/**
 * GitLab connection — its runner supports backfill too, so the trigger is offered exactly as it is for
 * GitHub. The card reads the capability off the status, so nothing here is keyed to the vendor name.
 */
export const GitLab: Story = {
	args: {
		label: "GitLab",
		status: { ...baseStatus, kind: "GITLAB" },
	},
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /sync now/i })).toBeInTheDocument();
		await userEvent.click(canvas.getByRole("button", { name: /backfill/i }));
		await expect(args.onBackfill).toHaveBeenCalledTimes(1);
	},
};

/**
 * A kind whose runner offers no backfill pass — the trigger is withheld rather than offered and then
 * rejected with a 409. Sync now stays available.
 */
export const BackfillUnsupported: Story = {
	args: {
		status: { ...baseStatus, backfillSupported: false },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /sync now/i })).toBeInTheDocument();
		await expect(canvas.queryByRole("button", { name: /backfill/i })).not.toBeInTheDocument();
	},
};
