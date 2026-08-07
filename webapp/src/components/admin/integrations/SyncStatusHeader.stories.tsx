import type { Meta, StoryObj } from "@storybook/react";
import { ExternalLinkIcon } from "lucide-react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { ConnectionSyncStatus, SyncJob } from "@/api/types.gen";
import { buttonVariants } from "@/components/ui/button";
import { SyncStatusHeader } from "./SyncStatusHeader";

const minutesAgo = (minutes: number) => new Date(Date.now() - minutes * 60_000);
const minutesFromNow = (minutes: number) => new Date(Date.now() + minutes * 60_000);

const SYNC_INTERVAL_SECONDS = 3_600;

const baseStatus: ConnectionSyncStatus = {
	connectionId: 7,
	connectionState: "ACTIVE",
	kind: "GITHUB",
	health: "HEALTHY",
	resourceCounts: { total: 12, errored: 0, pending: 0, stale: 0 },
	backfillSupported: true,
	syncIntervalSeconds: SYNC_INTERVAL_SECONDS,
	lastSuccessfulSyncAt: minutesAgo(4),
	nextScheduledSyncAt: minutesFromNow(56),
	lastEventProcessedAt: minutesAgo(1),
	webhookRegistered: true,
	rateLimit: {
		limit: 5000,
		remaining: 4812,
		resetAt: minutesFromNow(40),
		observedAt: minutesAgo(2),
	},
};

const runningJob: SyncJob = {
	id: 12,
	type: "RECONCILIATION",
	trigger: "MANUAL",
	status: "RUNNING",
	cancelRequested: false,
	createdAt: minutesAgo(1),
	startedAt: minutesAgo(1),
	itemsProcessed: 5,
	itemsTotal: 12,
};

const meta = {
	component: SyncStatusHeader,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		label: "GitHub",
		status: baseStatus,
		isLoading: false,
		isConnectionActive: true,
		triggeringType: null,
		isCancelling: false,
		onRetry: fn(),
		onSync: fn(),
		onBackfill: fn(),
		onCancel: fn(),
	},
} satisfies Meta<typeof SyncStatusHeader>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Healthy: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByLabelText(/connection health/i)).toHaveTextContent("Healthy");
	},
};

export const StaleFreshness: Story = {
	args: {
		status: { ...baseStatus, health: "DEGRADED", lastSuccessfulSyncAt: minutesAgo(150) },
	},
};

export const VeryStaleFreshness: Story = {
	args: {
		status: { ...baseStatus, health: "DEGRADED", lastSuccessfulSyncAt: minutesAgo(60 * 11) },
	},
};

export const NextRunDue: Story = {
	args: { status: { ...baseStatus, nextScheduledSyncAt: minutesAgo(2) } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/next run due/i)).toBeInTheDocument();
	},
};

export const UnknownCadence: Story = {
	args: {
		status: {
			...baseStatus,
			syncIntervalSeconds: undefined,
			nextScheduledSyncAt: undefined,
			lastSuccessfulSyncAt: minutesAgo(60 * 20),
			lastEventProcessedAt: undefined,
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.queryByRole("button", { name: /stale/i })).not.toBeInTheDocument();
		await expect(canvas.getByText(/ago$/)).toBeInTheDocument();
	},
};

export const NeverSynced: Story = {
	args: {
		status: { ...baseStatus, health: "PENDING", lastSuccessfulSyncAt: undefined },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Never synced")).toBeInTheDocument();
	},
};

export const NothingToSyncYet: Story = {
	args: {
		label: "Slack",
		status: {
			...baseStatus,
			kind: "SLACK",
			health: "PENDING",
			backfillSupported: false,
			lastSuccessfulSyncAt: undefined,
			rateLimit: undefined,
			resourceCounts: { total: 0, errored: 0, pending: 0, stale: 0 },
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/no resources to sync yet/i)).toBeInTheDocument();
		await expect(canvas.queryByText(/never synced/i)).not.toBeInTheDocument();
	},
};

export const WebhookNotRegistered: Story = {
	args: { status: { ...baseStatus, webhookRegistered: false } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/not registered/i)).toBeInTheDocument();
	},
};

export const NoWebhookEventsYet: Story = {
	args: { status: { ...baseStatus, lastEventProcessedAt: undefined } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/no events yet/i)).toBeInTheDocument();
	},
};

export const WebhookNotTracked: Story = {
	args: {
		status: {
			...baseStatus,
			kind: "GITLAB",
			webhookRegistered: undefined,
			lastEventProcessedAt: undefined,
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.queryByText(/^webhook$/i)).not.toBeInTheDocument();
		await expect(canvas.queryByText(/no events yet/i)).not.toBeInTheDocument();
		await expect(canvas.queryByText(/not registered/i)).not.toBeInTheDocument();
		await expect(canvas.getByText(/rate limit/i)).toBeInTheDocument();
	},
};

export const NoDiagnosticsAtAll: Story = {
	args: {
		label: "Slack",
		status: {
			...baseStatus,
			kind: "SLACK",
			backfillSupported: false,
			rateLimit: undefined,
			webhookRegistered: undefined,
			lastEventProcessedAt: undefined,
		},
		onBackfill: undefined,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.queryByRole("listitem")).not.toBeInTheDocument();
	},
};

export const RateLimitNearlyExhausted: Story = {
	args: {
		status: {
			...baseStatus,
			health: "DEGRADED",
			rateLimit: {
				limit: 5000,
				remaining: 220,
				resetAt: minutesFromNow(12),
				observedAt: minutesAgo(1),
			},
		},
	},
};

export const RateLimitResetTooltip: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.hover(canvas.getByText("4,812"));
		await expect(await screen.findByText(/resets in/i)).toBeInTheDocument();
	},
};

export const RateLimitThrottled: Story = {
	args: {
		label: "Slack",
		status: {
			...baseStatus,
			kind: "SLACK",
			health: "DEGRADED",
			backfillSupported: false,
			rateLimit: { observedAt: minutesAgo(1), throttledUntil: minutesFromNow(1) },
		},
		onBackfill: undefined,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const reading = canvas.getByText(/throttled/i);
		await expect(reading).toHaveTextContent(/retry in/i);
		await expect(canvas.queryByText(/^\/\s*[\d,]+$/)).not.toBeInTheDocument();
	},
};

export const RateLimitCeilingOnly: Story = {
	args: {
		status: {
			...baseStatus,
			rateLimit: { limit: 5000, observedAt: minutesAgo(90) },
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/limit 5,000/i)).toBeInTheDocument();
		await expect(canvas.queryByText(/^\/\s*[\d,]+$/)).not.toBeInTheDocument();
	},
};

export const RateLimitNotReported: Story = {
	args: {
		status: {
			...baseStatus,
			rateLimit: { observedAt: minutesAgo(90), throttledUntil: minutesAgo(30) },
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.queryByText(/rate limit/i)).not.toBeInTheDocument();
		await expect(canvas.queryByText(/throttled/i)).not.toBeInTheDocument();
	},
};

export const ScheduledBackfill: Story = {
	args: { status: { ...baseStatus, backfill: { state: "IN_PROGRESS", percent: 40 } } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("In Progress")).toBeInTheDocument();
		await expect(canvas.queryByText(/IN_PROGRESS/)).not.toBeInTheDocument();
	},
};

export const BackfillFromSplitMenu: Story = {
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /more sync options/i }));
		await userEvent.click(await screen.findByRole("menuitem", { name: /run backfill/i }));
		await expect(args.onBackfill).toHaveBeenCalledTimes(1);
	},
};

export const BackfillUnsupported: Story = {
	args: { status: { ...baseStatus, backfillSupported: false } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			canvas.queryByRole("button", { name: /more sync options/i }),
		).not.toBeInTheDocument();
	},
};

export const SyncTriggerPending: Story = {
	args: { triggeringType: "RECONCILIATION" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /starting…/i })).toBeDisabled();
		await expect(canvas.getByRole("button", { name: /more sync options/i })).toBeDisabled();
	},
};

export const ActiveJobRunning: Story = {
	args: { status: { ...baseStatus, activeJob: runningJob } },
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByLabelText(/connection health/i)).toHaveTextContent("Syncing");
		const cancel = canvas.getByRole("button", { name: /^cancel$/i });
		await expect(cancel).toBeEnabled();
		await userEvent.click(cancel);
		await expect(args.onCancel).toHaveBeenCalledTimes(1);
	},
};

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

export const Slack: Story = {
	args: {
		label: "Slack",
		status: {
			...baseStatus,
			kind: "SLACK",
			backfillSupported: false,
			rateLimit: undefined,
			webhookRegistered: undefined,
			resourceCounts: { total: 3, errored: 0, pending: 0, stale: 0 },
		},
		onBackfill: undefined,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.queryByText(/rate limit/i)).not.toBeInTheDocument();
		await expect(canvas.getByText(/^webhook$/i)).toBeInTheDocument();
		await expect(canvas.queryByText(/not registered/i)).not.toBeInTheDocument();
	},
};

export const WithActions: Story = {
	args: {
		actions: (
			<a
				href="https://github.com/settings/installations"
				target="_blank"
				rel="noreferrer"
				className={buttonVariants({ variant: "outline", size: "sm" })}
			>
				Manage installation on GitHub
				<ExternalLinkIcon className="size-3.5" />
			</a>
		),
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const link = canvas.getByText(/manage installation on github/i).closest("a");
		await expect(link).toHaveAttribute("href", "https://github.com/settings/installations");
	},
};

export const ConnectionInactive: Story = {
	args: {
		isConnectionActive: false,
		status: { ...baseStatus, connectionState: "SUSPENDED", health: "SUSPENDED" },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.queryByRole("button", { name: /sync now/i })).not.toBeInTheDocument();
	},
};

export const Loading: Story = { args: { status: undefined, isLoading: true } };

export const Missing: Story = { args: { status: undefined, isConnectionActive: false } };

export const LoadError: Story = {
	args: { status: undefined, error: new Error("503 Service Unavailable") },
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/couldn't load the github connection/i)).toBeInTheDocument();
		await userEvent.click(canvas.getByRole("button", { name: /retry/i }));
		await expect(args.onRetry).toHaveBeenCalledTimes(1);
	},
};
