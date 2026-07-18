import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "storybook/test";
import type { ConnectionSyncStatus, SyncJob } from "@/api/types.gen";
import { IntegrationOverviewCard } from "./IntegrationOverviewCard";

const minutesAgo = (minutes: number) => new Date(Date.now() - minutes * 60_000);

/** Hourly reconciliation — what every freshness reading on the card is judged against. */
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

/**
 * The integrations landing tile for one connection. It surfaces health (a running job flips the badge
 * to "Syncing"), last-sync/last-event, the errored and stale resource counts, and a sync + details
 * footer while active — and it degrades honestly: a not-connected SCM explains it is chosen at
 * workspace creation, a non-ACTIVE connection hides its controls, and a failed status query shows an
 * inline error rather than a blank card.
 *
 * This is the triage page, so its freshness reading is tinted against the connection's own cadence:
 * a card is picked out of the grid by colour rather than by reading four dates in sequence.
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

/** Some resources are erroring — surfaced as a destructive count. */
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
		await expect(canvas.getByText("3 errored")).toHaveClass("text-destructive");
		await expect(canvas.getByText(/of 12 resources/i)).toBeInTheDocument();
	},
};

/**
 * Stale resources with nothing errored — the quieter, more common failure: a connection whose
 * scheduler has stopped reports HEALTHY forever while its mirror rots, so the stale count is what
 * surfaces it.
 */
export const WithStaleResources: Story = {
	args: { status: { ...status, resourceCounts: { total: 12, errored: 0, pending: 0, stale: 4 } } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("4 stale")).toHaveClass("text-warning");
	},
};

/** Both at once — two tinted numbers in one line, and still exactly one badge on the card. */
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
		await expect(canvas.getByText("1 errored")).toBeInTheDocument();
		await expect(canvas.getByText("3 stale")).toBeInTheDocument();
	},
};

/**
 * The sync is two cadences overdue. The reading tints itself, which is the entire point of a triage
 * grid — this card is findable at a glance among healthy siblings.
 */
export const StaleFreshness: Story = {
	args: { status: { ...status, lastSuccessfulSyncAt: minutesAgo(200) } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/hours ago$/)).toHaveClass("text-warning");
	},
};

/** No cadence from the server means no judgement here — the age is printed, not coloured. */
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
		const reading = canvas.getByText(/ago$/);
		await expect(reading).not.toHaveClass("text-warning");
		await expect(reading).not.toHaveClass("text-destructive");
	},
};

/** Nothing has ever synced, and no webhook event has arrived either. */
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
		await expect(canvas.getByText("Never synced")).toBeInTheDocument();
		await expect(canvas.getByText(/no events received yet/i)).toBeInTheDocument();
	},
};

/**
 * The status query failed. It renders the same `QueryErrorAlert` as its siblings, and a 503 is
 * retryable, so the button is offered.
 */
export const StatusError: Story = {
	args: {
		status: undefined,
		isStatusError: true,
		statusError: { status: 503, detail: "The GitHub API is unavailable." },
		onRetryStatus: fn(),
	},
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/couldn't load sync status/i)).toBeInTheDocument();
		await expect(canvas.getByText(/github api is unavailable/i)).toBeInTheDocument();
		await userEvent.click(canvas.getByRole("button", { name: /retry/i }));
		await expect(args.onRetryStatus).toHaveBeenCalledTimes(1);
	},
};

/**
 * A 403 on the status query. Retrying an authorization failure cannot succeed, so no Retry button is
 * offered — the alert says what to do instead. Same component, same call site, different way out.
 */
export const StatusErrorForbidden: Story = {
	args: {
		status: undefined,
		isStatusError: true,
		statusError: { status: 403, detail: "You are not an admin of this workspace." },
		onRetryStatus: fn(),
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/not an admin of this workspace/i)).toBeInTheDocument();
		await expect(canvas.queryByRole("button", { name: /retry/i })).not.toBeInTheDocument();
	},
};

/**
 * Connection exists but is suspended — controls are hidden and the copy says what stopped and what to
 * do, reading identically to the suspended state on every other integration.
 */
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
		await expect(canvas.getByText(/syncing is paused/i)).toBeInTheDocument();
		await expect(canvas.getByText(/suspended by the provider/i)).toBeInTheDocument();
		await expect(canvas.queryByText(/connection is suspended/i)).not.toBeInTheDocument();
	},
};

/**
 * Setup hasn't finished. PENDING resolves on its own and costs the admin nothing, so unlike
 * SUSPENDED/UNINSTALLED it is stated plainly rather than as a warning.
 */
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
		await expect(canvas.getByText(/finishing setup/i)).toBeInTheDocument();
		await expect(canvas.queryByText(/slack is pending/i)).not.toBeInTheDocument();
	},
};

/**
 * The app was removed upstream. The copy names the consequence (nothing is syncing) and the fix
 * (reconnect).
 */
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
		await expect(canvas.getByText(/the app was removed/i)).toBeInTheDocument();
		await expect(canvas.queryByText(/slack is uninstalled/i)).not.toBeInTheDocument();
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
