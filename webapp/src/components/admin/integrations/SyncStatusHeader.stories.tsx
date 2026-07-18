import type { Meta, StoryObj } from "@storybook/react";
import { ExternalLinkIcon } from "lucide-react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { ConnectionSyncStatus, SyncJob } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { SyncStatusHeader } from "./SyncStatusHeader";

const minutesAgo = (minutes: number) => new Date(Date.now() - minutes * 60_000);
const minutesFromNow = (minutes: number) => new Date(Date.now() + minutes * 60_000);

/** Hourly reconciliation — the cadence every freshness reading below is judged against. */
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

/**
 * The connection plane for every integration: health, freshness, and the controls that change them.
 *
 * The headline is one sentence — badge, when the mirror last completed, when it next runs — because
 * that is the whole question this page is opened to answer. The reading is tinted against the
 * connection's own cadence, so "4 hours ago" reads as fine on a six-hourly schedule and as a missed
 * run on an hourly one.
 *
 * Everything under it qualifies that sentence: diagnostics that explain a bad reading, the running
 * job, and one trigger. Backfill is a menu item rather than a second button, so a pending state can
 * only mean the one visible trigger.
 */
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

/**
 * Connected, healthy, synced within cadence. The freshness reading is uncoloured because it is fine,
 * and the schedule behind it is stated — a freshness claim with no cadence to read it against is not
 * interpretable.
 */
export const Healthy: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByLabelText(/connection health/i)).toHaveTextContent("Healthy");
		await expect(canvas.getByText(/last synced/i)).toBeInTheDocument();
		await expect(canvas.getByText(/next run in/i)).toBeInTheDocument();
	},
};

/**
 * Two cadences without a successful run — the reading is tinted `text-warning`, because a reading is
 * only judged once the route passes the cadence behind it.
 */
export const StaleFreshness: Story = {
	args: {
		status: { ...baseStatus, health: "DEGRADED", lastSuccessfulSyncAt: minutesAgo(150) },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/hours ago$/)).toHaveClass("text-warning");
	},
};

/** Six cadences past due: the reading goes destructive well before anything else reports a fault. */
export const VeryStaleFreshness: Story = {
	args: {
		status: { ...baseStatus, health: "DEGRADED", lastSuccessfulSyncAt: minutesAgo(60 * 11) },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/hours ago$/)).toHaveClass("text-destructive");
	},
};

/**
 * A schedule that is already due. "next run 5 minutes ago" would read as a past event rather than a
 * pending one, so an overdue cron says so plainly — the worker may simply be busy.
 */
export const NextRunDue: Story = {
	args: { status: { ...baseStatus, nextScheduledSyncAt: minutesAgo(2) } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/next run due/i)).toBeInTheDocument();
	},
};

/**
 * No cadence — the schedule is irregular or unparseable, so the server sends null rather than guess.
 * The reading is printed and deliberately not judged; declining to colour is the honest answer.
 */
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
		const reading = canvas.getByText(/ago$/);
		await expect(reading).not.toHaveClass("text-warning");
		await expect(reading).not.toHaveClass("text-destructive");
	},
};

/** Nothing has ever synced, and there are resources that should have. */
export const NeverSynced: Story = {
	args: {
		status: { ...baseStatus, health: "PENDING", lastSuccessfulSyncAt: undefined },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Never synced")).toBeInTheDocument();
	},
};

/**
 * A connection with nothing to sync yet — a fresh Slack workspace with no channels activated. "Never
 * synced" here is an accusation the facts don't support, so the copy follows the resource count.
 */
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

/** The vendor webhook is not registered — the card flags it instead of implying live delivery. */
export const WebhookNotRegistered: Story = {
	args: { status: { ...baseStatus, webhookRegistered: false } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/not registered/i)).toBeInTheDocument();
	},
};

/** Registered, but nothing has arrived through it yet. A dash here would read as a value, not a gap. */
export const NoWebhookEventsYet: Story = {
	args: { status: { ...baseStatus, lastEventProcessedAt: undefined } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/no events yet/i)).toBeInTheDocument();
	},
};

/**
 * No webhook is tracked for this connection at all — a GitLab project whose hook was never registered
 * through us, a PAT-backed SCM connection, or Slack, whose events arrive through the app's own
 * subscription. `webhookRegistered` is null (the DTO's "not applicable/unknown") and no event has ever
 * been observed, so there is nothing measured to report.
 *
 * The row is dropped rather than rendered as "Webhook — No events yet", which is the same lie the
 * gated rate-limit row already refuses to tell: an unobserved thing is not a broken thing. `false` is
 * a different case and still shows (see WebhookNotRegistered) — that IS a measurement.
 */
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
		// The diagnostics row survives — the rate limit is still a real observation.
		await expect(canvas.getByText(/rate limit/i)).toBeInTheDocument();
	},
};

/**
 * A connection that reports no webhook, no rate limit and no backfill — every diagnostic gated off.
 * The whole row goes rather than leaving an empty, padded strip under the freshness sentence.
 */
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
		await expect(canvasElement.querySelector('[data-slot="item-group"]')).toBeNull();
		await expect(canvas.queryByRole("listitem")).not.toBeInTheDocument();
		// The headline and the trigger are untouched — only the qualifiers are gone.
		await expect(canvas.getByText(/last synced/i)).toBeInTheDocument();
		await expect(canvas.getByRole("button", { name: /sync now/i })).toBeInTheDocument();
	},
};

/**
 * The rate limit is nearly spent — the only gauge reading that earns colour, because it is the only one
 * that predicts a failure.
 */
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
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("220")).toHaveClass("text-warning");
	},
};

/** The reset window is the one fact a bare number loses, so it lives one hover away. */
export const RateLimitResetTooltip: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.hover(canvas.getByText("4,812"));
		await expect(await screen.findByText(/resets in/i)).toBeInTheDocument();
	},
};

/**
 * The vendor told us to back off (a 429 with a `Retry-After`) — Slack's and a throttled Outline's only
 * real signal. There is no budget to gauge at that instant, so the reading is the transient back-off
 * state and nothing else, tinted because it is actively degrading the sync.
 */
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
		await expect(reading).toHaveClass("text-warning");
		await expect(reading).toHaveTextContent(/retry in/i);
		// No gauge is invented from a throttle — the "/ N" remainder span a gauge draws is absent.
		await expect(canvas.queryByText(/^\/\s*[\d,]+$/)).not.toBeInTheDocument();
	},
};

/**
 * Ceiling known, live remaining not reported — the window budget was observed (or REST-seeded) but the
 * per-request remaining has since rolled over or was never sent. The ceiling is a real fact, so it
 * shows; a fabricated `— / N` gauge would not. Muted, because it is not predicting a failure.
 */
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
		// A ceiling is not a gauge — the "/ N" remainder span a gauge draws is absent.
		await expect(canvas.queryByText(/^\/\s*[\d,]+$/)).not.toBeInTheDocument();
	},
};

/**
 * The snapshot exists — the vendor was observed — but nothing in it is renderable now: a throttle that
 * has since lapsed, with no known ceiling. The honest display is the absence of the row, so the "Rate
 * limit" diagnostic is dropped rather than shown with a blank or zeroed value.
 */
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

/**
 * The scheduled backfill cycle's own state. "Disabled" here means the background cycle is off — not
 * that the Run backfill action is unavailable, which is a manual trigger and a different question.
 * `IN_PROGRESS` reaches the admin title-cased rather than as a raw wire token.
 */
export const ScheduledBackfill: Story = {
	args: { status: { ...baseStatus, backfill: { state: "IN_PROGRESS", percent: 40 } } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("In Progress")).toBeInTheDocument();
		await expect(canvas.queryByText(/IN_PROGRESS/)).not.toBeInTheDocument();
	},
};

/** Backfill lives in the split menu — one visible trigger, so pending can only mean this trigger. */
export const BackfillFromSplitMenu: Story = {
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /more sync options/i }));
		await userEvent.click(await screen.findByRole("menuitem", { name: /run backfill/i }));
		await expect(args.onBackfill).toHaveBeenCalledTimes(1);
	},
};

/** A kind whose runner offers no backfill pass — the menu is withheld rather than offered and 409'd. */
export const BackfillUnsupported: Story = {
	args: { status: { ...baseStatus, backfillSupported: false } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /sync now/i })).toBeInTheDocument();
		await expect(
			canvas.queryByRole("button", { name: /more sync options/i }),
		).not.toBeInTheDocument();
	},
};

/** *Sync now* was pressed — one trigger, one pending state, and no second button to lie about it. */
export const SyncTriggerPending: Story = {
	args: { triggeringType: "RECONCILIATION" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /starting…/i })).toBeDisabled();
		await expect(canvas.getByRole("button", { name: /more sync options/i })).toBeDisabled();
	},
};

/** A job is running — the trigger disables, progress appears, and a cooperative Cancel joins the group. */
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
 * Slack: the same component, minus the diagnostics whose payload it never sends. There is no rate
 * limit and no backfill, so those items are absent rather than dashed — an integration should not have
 * to render an empty field to prove it doesn't have one.
 *
 * Slack tracks no per-connection webhook registration (events arrive through the app's own event
 * subscription), so `webhookRegistered` is null. Once an event has been processed the row is still
 * worth showing — the timestamp is a real measurement — so it appears with the reading and no
 * "Not registered" claim behind it.
 */
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

/** An app-installation workspace gets a link out to manage the install on GitHub. */
export const WithActions: Story = {
	args: {
		actions: (
			<Button
				variant="outline"
				size="sm"
				nativeButton={false}
				render={
					<a href="https://github.com/settings/installations" target="_blank" rel="noreferrer" />
				}
			>
				Manage installation on GitHub
				<ExternalLinkIcon className="size-3.5" />
			</Button>
		),
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const link = canvas.getByText(/manage installation on github/i).closest("a");
		await expect(link).toHaveAttribute("href", "https://github.com/settings/installations");
	},
};

/** A non-ACTIVE connection keeps its readings and loses its controls — nothing here would succeed. */
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

/**
 * Status still loading. The placeholder reproduces the loaded card's structure — headline, diagnostics
 * row, action row — so the content lands in a box that is already the right size.
 */
export const Loading: Story = { args: { status: undefined, isLoading: true } };

/** No connection row for this workspace — a plain informational line, not an error. */
export const Missing: Story = { args: { status: undefined, isConnectionActive: false } };

/** The status query failed — a retryable inline error replaces the readings. */
export const LoadError: Story = {
	args: { status: undefined, error: new Error("503 Service Unavailable") },
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/couldn't load the github connection/i)).toBeInTheDocument();
		await userEvent.click(canvas.getByRole("button", { name: /retry/i }));
		await expect(args.onRetry).toHaveBeenCalledTimes(1);
	},
};
