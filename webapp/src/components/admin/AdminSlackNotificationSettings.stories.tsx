import type { Meta, StoryObj } from "@storybook/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { AdminSlackNotificationSettings } from "./AdminSlackNotificationSettings";

/**
 * Admin card for the weekly Slack leaderboard digest. The component owns its form
 * state and is remounted (via a server-snapshot `key` in the parent) whenever the
 * server truth changes, so each story here represents one immutable initial snapshot.
 */
const meta = {
	component: AdminSlackNotificationSettings,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	// useMutation needs a client; stories never hit the network (no play triggers a save).
	decorators: [
		(Story) => (
			<QueryClientProvider client={new QueryClient()}>
				<Story />
			</QueryClientProvider>
		),
	],
	args: {
		workspaceSlug: "demo-workspace",
		hasSlackConnection: false,
		enabled: false,
		scheduleDay: 1,
		scheduleTime: "09:00",
		onSaved: fn(),
	},
} satisfies Meta<typeof AdminSlackNotificationSettings>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Cold start — admin hasn't connected the workspace yet. */
export const NotConnected: Story = {
	args: { hasSlackConnection: false },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			canvas.getByRole("button", { name: /connect slack workspace/i }),
		).toBeInTheDocument();
	},
};

/**
 * OAuth completed but no channel picked yet — the Send-test button must stay disabled
 * because there is no valid channel to probe.
 */
export const ConnectedNoChannel: Story = {
	args: { hasSlackConnection: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /send test message/i })).toBeDisabled();
	},
};

/** Fully configured: connected, channel + team filter set, digest enabled. */
export const ConnectedConfigured: Story = {
	args: {
		hasSlackConnection: true,
		channelId: "C0974LJBPBK",
		teamLabel: "engineering",
		enabled: true,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// Valid channel + valid time ⇒ both actions are live.
		await expect(canvas.getByRole("button", { name: /^save$/i })).toBeEnabled();
		await expect(canvas.getByRole("button", { name: /send test message/i })).toBeEnabled();
	},
};

/**
 * Toggling the digest Switch off flips the control's checked state — verifies the
 * Switch is locally controlled (no prop→state effect resets it mid-edit).
 */
export const ToggleDigestOff: Story = {
	args: {
		hasSlackConnection: true,
		channelId: "C0974LJBPBK",
		teamLabel: "engineering",
		enabled: true,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const digest = canvas.getByRole("switch", { name: /send weekly digest/i });
		await expect(digest).toBeChecked();
		await userEvent.click(digest);
		await expect(digest).not.toBeChecked();
	},
};

/** Non-default day — the day Select renders the label ("Thursday"), not the raw value. */
export const NonDefaultDay: Story = {
	args: {
		hasSlackConnection: true,
		channelId: "C0974LJBPBK",
		enabled: true,
		scheduleDay: 4,
		scheduleTime: "14:30",
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Thursday")).toBeInTheDocument();
	},
};

/** Invalid channel id — the field shows its format error and Save is disabled. */
export const InvalidChannel: Story = {
	args: {
		hasSlackConnection: true,
		channelId: "not-a-channel",
		enabled: true,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/Channel IDs start with C \/ G \/ D/i)).toBeVisible();
		await expect(canvas.getByRole("button", { name: /^save$/i })).toBeDisabled();
	},
};

/** Invalid time — the time field surfaces its HH:mm error and Save is disabled. */
export const InvalidTime: Story = {
	args: {
		hasSlackConnection: true,
		channelId: "C0974LJBPBK",
		enabled: true,
		scheduleTime: "9am",
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/Time must be in HH:mm format\./i)).toBeVisible();
		await expect(canvas.getByRole("button", { name: /^save$/i })).toBeDisabled();
	},
};

/**
 * Disconnect affordance — present only when the server exposes the active connection id.
 * The play opens the confirmation dialog and asserts the destructive copy.
 */
export const ConnectedWithDisconnect: Story = {
	args: {
		hasSlackConnection: true,
		slackConnectionId: 42,
		channelId: "C0974LJBPBK",
		teamLabel: "engineering",
		enabled: true,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const trigger = canvas.getByRole("button", { name: /disconnect slack/i });
		await expect(trigger).toBeInTheDocument();
		await userEvent.click(trigger);
		// AlertDialog renders in a portal — query the whole document, not just the canvas.
		const dialog = await screen.findByRole("alertdialog", { name: /disconnect slack\?/i });
		await expect(dialog).toBeInTheDocument();
		// Confirm copy + the destructive confirm action are present.
		await expect(within(dialog).getByText(/the bot will be uninstalled/i)).toBeInTheDocument();
		await expect(within(dialog).getByRole("button", { name: /^disconnect$/i })).toBeInTheDocument();
	},
};
