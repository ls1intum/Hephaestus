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

/**
 * One control for one value: the digest channel is chosen from a combobox that shows
 * `#channel-name`. The stable Slack id is held in state and is never dumped into a visible text
 * box. Disabled options carry a reason instead of vanishing from the list.
 */
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

		// The options live in a portalled popover — open the combobox, then query the document.
		// Scope by name: the schedule Day <Select> is also exposed as role="combobox".
		const trigger = canvas.getByRole("combobox", { name: /digest channel/i });
		await userEvent.click(trigger);

		await expect(await screen.findByRole("option", { name: /#private-team/i })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
		await expect(screen.getByText(/needs invite/i)).toBeInTheDocument();

		// Search narrows the option list to the match.
		await userEvent.type(
			screen.getByRole("combobox", { name: /search digest slack channels/i }),
			"gen",
		);
		await expect(screen.getByRole("option", { name: /#general/i })).toBeInTheDocument();
		await expect(screen.queryByRole("option", { name: /#private-team/i })).not.toBeInTheDocument();

		await userEvent.click(screen.getByRole("option", { name: /#general/i }));
		await expect(trigger).toHaveTextContent("#general");
		await expect(canvas.queryByDisplayValue("C01GENERAL01")).not.toBeInTheDocument();
	},
};

/** A channel Slack never listed is still reachable — the paste escape hatch resolves a link. */
export const PasteChannelLink: Story = {
	args: { hasSlackConnection: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// With no candidates the paste path is the only path, so it is already open.
		await userEvent.type(
			canvas.getByLabelText(/paste a channel link or id/i),
			"https://acme.slack.com/archives/C0974LJBPBK",
		);
		await expect(canvas.getByRole("button", { name: /send test message/i })).toBeEnabled();
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
		await expect(within(dialog).getByText(/the bot is uninstalled/i)).toBeInTheDocument();
		await expect(within(dialog).getByRole("button", { name: /^disconnect$/i })).toBeInTheDocument();
	},
};
