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
 * action row. Actions vary by provider — GitHub gets a Backfill trigger and, for app installs, a
 * "Manage installation" link — and a running job exposes a cooperative Cancel that flips to
 * "Stopping…" once requested. Load failures show a retryable error instead of a blank card.
 */
const meta = {
	component: ScmConnectionCard,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		provider: "GITHUB",
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

/** GitHub with a backfill in flight — the rollup line and the Backfill trigger are shown. */
export const GitHubWithBackfill: Story = {
	args: {
		status: { ...baseStatus, backfill: { state: "RUNNING", percent: 40 } },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/RUNNING — 40%/i)).toBeInTheDocument();
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

/** GitLab connection — no Backfill trigger (GitHub-only) and provider-specific labelling. */
export const GitLab: Story = {
	args: {
		provider: "GITLAB",
		label: "GitLab",
		status: { ...baseStatus, kind: "GITLAB" },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /sync now/i })).toBeInTheDocument();
		await expect(canvas.queryByRole("button", { name: /backfill/i })).not.toBeInTheDocument();
	},
};

/** The rate-limit budget is running low — the gauge reads near-empty. */
export const RateLimitLow: Story = {
	args: {
		status: {
			...baseStatus,
			rateLimit: { limit: 5000, remaining: 90, resetAt: new Date(Date.now() + 8 * 60 * 1000) },
		},
	},
};
