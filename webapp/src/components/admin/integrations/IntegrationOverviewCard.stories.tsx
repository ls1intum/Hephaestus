import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, within } from "storybook/test";
import type { ConnectionSyncStatus, SyncJob } from "@/api/types.gen";
import { IntegrationOverviewCard } from "./IntegrationOverviewCard";

const status: ConnectionSyncStatus = {
	connectionId: 7,
	connectionState: "ACTIVE",
	kind: "GITHUB",
	health: "HEALTHY",
	resourceCounts: { total: 12, errored: 0 },
	backfillSupported: true,
	lastSuccessfulSyncAt: new Date("2026-07-14T10:00:00Z"),
};

const runningJob: SyncJob = {
	id: 11,
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
 * The integrations landing tile for one connection. It surfaces health (a running job flips the
 * badge to "Syncing"), last-sync/last-event, an errored-resources warning and a sync + details
 * footer while active — and it degrades honestly: a not-connected SCM explains it is chosen at
 * workspace creation, a non-ACTIVE connection hides its controls, and a failed status query shows
 * an inline error rather than a blank card.
 */
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

/** Connected + healthy — the steady state, with sync + details controls. */
export const Connected: Story = {};

/** A running job supersedes health: the badge reads "Syncing" and progress is shown. */
export const Syncing: Story = {
	args: { status: { ...status, health: "FAILED", activeJob: runningJob } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByLabelText(/connection health/i)).toHaveTextContent("Syncing");
	},
};

/** Some resources are erroring — surfaced as a destructive count line. */
export const WithErroredResources: Story = {
	args: {
		status: { ...status, health: "DEGRADED", resourceCounts: { total: 12, errored: 3 } },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/3 of 12 resources errored/i)).toBeInTheDocument();
	},
};

/** The status query failed — an inline error replaces the metrics, controls stay put. */
export const StatusError: Story = {
	args: { status: undefined, isStatusError: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/couldn't load sync status/i)).toBeInTheDocument();
	},
};

/** Connection exists but is suspended — controls are hidden and the copy says why. */
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
		await expect(canvas.getByText(/connection is suspended/i)).toBeInTheDocument();
	},
};

/** SCM not connected — there is no Connect button; it is chosen at workspace creation. */
export const ScmNotConnected: Story = {
	args: {
		entry: { kind: "GITHUB", displayName: "GitHub", connected: false },
		status: undefined,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			canvas.getByText(/source control is selected when the workspace is created/i),
		).toBeInTheDocument();
		await expect(canvas.queryByRole("link", { name: /connect/i })).not.toBeInTheDocument();
	},
};

/** A connectable (non-SCM) integration that is not yet connected shows a Connect action. */
export const Disconnected: Story = {
	args: {
		entry: { kind: "OUTLINE", displayName: "Outline", connected: false },
		status: undefined,
	},
};

/** Status is still loading — a skeleton stands in for the metrics. */
export const Loading: Story = { args: { status: undefined, isStatusLoading: true } };
