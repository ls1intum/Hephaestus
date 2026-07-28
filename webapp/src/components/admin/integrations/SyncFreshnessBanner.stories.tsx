import type { Meta, StoryObj } from "@storybook/react";
import { onlineManager } from "@tanstack/react-query";
import { expect, within } from "storybook/test";
import { SyncLivenessProvider } from "@/hooks/use-sync-liveness";
import { SyncFreshnessBanner } from "./SyncFreshnessBanner";

/**
 * The section-wide freshness note.
 *
 * This surface exists to tell the truth about how fresh sync data is, which makes silence about its
 * own staleness its worst possible failure — and offline was exactly that silence. Query v5 defaults
 * to `networkMode: "online"`, so a dropped connection *pauses* queries instead of failing them:
 * nothing throws, no `QueryErrorAlert` fires, and a live progress bar simply freezes mid-run looking
 * exactly like a healthy one. The SSE banner doesn't cover it either — `EventSource` re-enters
 * CONNECTING rather than CLOSED on a network drop.
 *
 * Offline strictly outranks live-push-lost (a dead network makes the polling fallback moot), so it
 * replaces the note rather than stacking a second bar.
 *
 * Online state is read from `onlineManager` rather than `navigator.onLine` so the banner and the data
 * can never disagree — it's the same signal that decides whether queries run.
 */
const meta = {
	component: SyncFreshnessBanner,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
	// `onlineManager` is a module-level singleton, so a story that fakes offline must put it back or
	// it leaks into every story that runs after it.
	beforeEach: () => {
		const wasOnline = onlineManager.isOnline();
		return () => onlineManager.setOnline(wasOnline);
	},
} satisfies Meta<typeof SyncFreshnessBanner>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Everything healthy — stream up, network up. The banner says nothing, which is the point. */
export const Healthy: Story = {
	beforeEach: () => {
		onlineManager.setOnline(true);
	},
	play: async ({ canvasElement }) => {
		await expect(within(canvasElement).queryByRole("status")).not.toBeInTheDocument();
	},
};

/**
 * The SSE stream is down but HTTP still works, so polling takes over. The data is still arriving,
 * just later than it should — stated plainly, not as an alarm.
 */
export const LivePushUnavailable: Story = {
	beforeEach: () => {
		onlineManager.setOnline(true);
	},
	decorators: [
		(Story) => (
			<SyncLivenessProvider livePushUnavailable>
				<Story />
			</SyncLivenessProvider>
		),
	],
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/live updates are unavailable/i)).toBeInTheDocument();
		await expect(canvas.getByText(/refreshing periodically/i)).toBeInTheDocument();
	},
};

/**
 * The browser went offline. Queries are paused, not failing, so nothing else on the page will ever
 * say so — this banner is the only thing standing between the admin and a frozen progress bar they'd
 * read as live.
 */
export const Offline: Story = {
	beforeEach: () => {
		onlineManager.setOnline(false);
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/you're offline/i)).toBeInTheDocument();
		await expect(canvas.getByText(/snapshot/i)).toBeInTheDocument();
		// Announced politely: the reader shouldn't be interrupted, but must not be left guessing.
		await expect(canvas.getByRole("status")).toBeInTheDocument();
	},
};

/**
 * Offline *and* the stream is down — the common case, since losing the network kills the SSE too.
 * Only the offline note shows: telling someone with no network that push is unavailable is noise.
 */
export const OfflineOutranksLivePush: Story = {
	beforeEach: () => {
		onlineManager.setOnline(false);
	},
	decorators: [
		(Story) => (
			<SyncLivenessProvider livePushUnavailable>
				<Story />
			</SyncLivenessProvider>
		),
	],
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/you're offline/i)).toBeInTheDocument();
		await expect(canvas.queryByText(/live updates are unavailable/i)).not.toBeInTheDocument();
	},
};
