import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "storybook/test";
import type { ConnectionSyncStatus, SyncJob } from "@/api/types.gen";
import { IntegrationOverviewCard } from "./IntegrationOverviewCard";

const minutesAgo = (minutes: number) => new Date(Date.now() - minutes * 60_000);

const SYNC_INTERVAL_SECONDS = 3_600;

const status: ConnectionSyncStatus = {
	connectionId: 7,
	connectionState: "ACTIVE",
	kind: "GITHUB",
	health: "HEALTHY",
	resourceCounts: { total: 12, errored: 0, pending: 0, stale: 0 },
	backfillSupported: true,
	syncIntervalSeconds: SYNC_INTERVAL_SECONDS,
	lastSuccessfulSyncAt: minutesAgo(4),
	lastEventProcessedAt: minutesAgo(1),
};

const runningJob: SyncJob = {
	id: 11,
	type: "RECONCILIATION",
	trigger: "MANUAL",
	status: "RUNNING",
	cancelRequested: false,
	createdAt: minutesAgo(2),
	startedAt: minutesAgo(2),
	itemsProcessed: 5,
	itemsTotal: 12,
};

const meta = {
	component: IntegrationOverviewCard,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		entry: {
			kind: "GITHUB",
			displayName: "GitHub",
			connected: true,
			connectionId: 7,
			connectionState: "ACTIVE",
		},
		status,
		onSync: fn(),
	},
} satisfies Meta<typeof IntegrationOverviewCard>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Connected: Story = {};

export const Syncing: Story = {
	args: { status: { ...status, health: "FAILED", activeJob: runningJob } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByLabelText(/connection health/i)).toHaveTextContent("Syncing");
	},
};

export const WithErroredResources: Story = {
	args: {
		status: {
			...status,
			health: "DEGRADED",
			resourceCounts: { total: 12, errored: 3, pending: 0, stale: 0 },
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText("3 errored");
		canvas.getByText(/of 12 resources/i);
	},
};

export const WithStaleResources: Story = {
	args: { status: { ...status, resourceCounts: { total: 12, errored: 0, pending: 0, stale: 4 } } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText("4 stale");
	},
};

export const WithErroredAndStaleResources: Story = {
	args: {
		status: {
			...status,
			health: "DEGRADED",
			resourceCounts: { total: 12, errored: 1, pending: 0, stale: 3 },
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText("1 errored");
		canvas.getByText("3 stale");
	},
};

export const StaleFreshness: Story = {
	args: { status: { ...status, lastSuccessfulSyncAt: minutesAgo(200) } },
};

export const UnknownCadence: Story = {
	args: {
		status: {
			...status,
			syncIntervalSeconds: undefined,
			lastSuccessfulSyncAt: minutesAgo(60 * 30),
			lastEventProcessedAt: undefined,
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.queryByRole("button", { name: /stale/i })).not.toBeInTheDocument();
		canvas.getByText(/ago$/);
	},
};

export const NeverSynced: Story = {
	args: {
		status: {
			...status,
			health: "PENDING",
			lastSuccessfulSyncAt: undefined,
			lastEventProcessedAt: undefined,
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText("Never synced");
		canvas.getByText(/no events received yet/i);
	},
};

export const StatusError: Story = {
	args: {
		status: undefined,
		isStatusError: true,
		statusError: { status: 503, detail: "The GitHub API is unavailable." },
		onRetryStatus: fn(),
	},
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText(/couldn't load sync status/i);
		canvas.getByText(/github api is unavailable/i);
		await userEvent.click(canvas.getByRole("button", { name: /retry/i }));
		await expect(args.onRetryStatus).toHaveBeenCalledTimes(1);
	},
};

export const StatusErrorForbidden: Story = {
	args: {
		status: undefined,
		isStatusError: true,
		statusError: { status: 403, detail: "You are not an admin of this workspace." },
		onRetryStatus: fn(),
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText(/not an admin of this workspace/i);
		await expect(canvas.queryByRole("button", { name: /retry/i })).not.toBeInTheDocument();
	},
};

export const ConnectionSuspended: Story = {
	args: {
		entry: {
			kind: "GITHUB",
			displayName: "GitHub",
			connected: true,
			connectionId: 7,
			connectionState: "SUSPENDED",
		},
		status: { ...status, connectionState: "SUSPENDED", health: "SUSPENDED" },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText(/syncing is paused/i);
		canvas.getByText(/suspended by the provider/i);
		await expect(canvas.queryByText(/connection is suspended/i)).not.toBeInTheDocument();
	},
};

export const ConnectionPending: Story = {
	args: {
		entry: {
			kind: "SLACK",
			displayName: "Slack",
			connected: true,
			connectionId: 9,
			connectionState: "PENDING",
		},
		status: undefined,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText(/finishing setup/i);
		await expect(canvas.queryByText(/slack is pending/i)).not.toBeInTheDocument();
	},
};

export const ConnectionUninstalled: Story = {
	args: {
		entry: {
			kind: "SLACK",
			displayName: "Slack",
			connected: true,
			connectionId: 9,
			connectionState: "UNINSTALLED",
		},
		status: undefined,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText(/the app was removed/i);
		await expect(canvas.queryByText(/slack is uninstalled/i)).not.toBeInTheDocument();
	},
};

export const ScmNotConnected: Story = {
	args: {
		entry: { kind: "GITHUB", displayName: "GitHub", connected: false },
		status: undefined,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText(/source control is selected when the workspace is created/i);
		await expect(canvas.queryByRole("link", { name: /connect/i })).not.toBeInTheDocument();
	},
};

export const Disconnected: Story = {
	args: {
		entry: { kind: "OUTLINE", displayName: "Outline", connected: false },
		status: undefined,
	},
};

export const Loading: Story = { args: { status: undefined, isStatusLoading: true } };
