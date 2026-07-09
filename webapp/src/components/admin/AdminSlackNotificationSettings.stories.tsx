import type { Meta, StoryObj } from "@storybook/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { AdminSlackNotificationSettings } from "./AdminSlackNotificationSettings";

const meta = {
	component: AdminSlackNotificationSettings,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
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

/** OAuth completed, but no digest channel is selected yet. */
export const ConnectedNoChannel: Story = {
	args: { hasSlackConnection: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /^save$/i })).toBeEnabled();
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
		await expect(canvas.getByRole("button", { name: /^save$/i })).toBeEnabled();
		await expect(canvas.getByRole("button", { name: /send test message/i })).toBeEnabled();
	},
};

/** Toggling the digest Switch off updates local form state. */
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

/** Slack-discovered channels can be selected without manual ID entry. */
export const WithChannelPicker: Story = {
	args: {
		hasSlackConnection: true,
		channelCandidates: [
			{
				slackChannelId: "C01GENERAL01",
				channelName: "general",
				privateChannel: false,
				member: true,
				archived: false,
			},
			{
				slackChannelId: "C02PRIVATE02",
				channelName: "private-team",
				privateChannel: true,
				member: false,
				archived: false,
			},
		],
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const general = canvas.getByRole("button", { name: /#general/i });
		const privateTeam = canvas.getByRole("button", { name: /#private-team/i });
		await expect(privateTeam).toBeDisabled();
		await userEvent.click(general);
		await expect(general).toHaveAttribute("aria-pressed", "true");
		await expect(canvas.getByLabelText(/digest channel/i)).toHaveValue("C01GENERAL01");
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
		await expect(canvas.getByText(/Paste a Slack channel URL/i)).toBeVisible();
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
