import type { Meta, StoryObj } from "@storybook/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { AdminSlackConnectionSettings } from "./AdminSlackConnectionSettings";

const meta = {
	component: AdminSlackConnectionSettings,
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
		onSaved: fn(),
	},
} satisfies Meta<typeof AdminSlackConnectionSettings>;

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
 * OAuth completed but no channel typed yet — the Send-test button stays disabled: the probe
 * needs an explicit channel (nothing persists a default channel since the digest removal).
 */
export const ConnectedNoChannel: Story = {
	args: { hasSlackConnection: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /send test message/i })).toBeDisabled();
	},
};

/** A valid channel id typed into the field keeps the Send-test probe enabled. */
export const ConnectedWithChannel: Story = {
	args: { hasSlackConnection: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.type(canvas.getByLabelText(/channel id/i), "C0974LJBPBK");
		await expect(canvas.getByRole("button", { name: /send test message/i })).toBeEnabled();
	},
};

/** Invalid channel id — the field shows its format error and the probe stays disabled. */
export const InvalidChannel: Story = {
	args: { hasSlackConnection: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.type(canvas.getByLabelText(/channel id/i), "not-a-channel");
		await expect(canvas.getByText(/Channel IDs start with C \/ G \/ D/i)).toBeVisible();
		await expect(canvas.getByRole("button", { name: /send test message/i })).toBeDisabled();
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
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const trigger = canvas.getByRole("button", { name: /disconnect slack/i });
		await expect(trigger).toBeInTheDocument();
		await userEvent.click(trigger);
		// AlertDialog renders in a portal — query the whole document, not just the canvas.
		const dialog = await screen.findByRole("alertdialog", { name: /disconnect slack\?/i });
		await expect(dialog).toBeInTheDocument();
		await expect(within(dialog).getByText(/the bot will be uninstalled/i)).toBeInTheDocument();
		await expect(within(dialog).getByRole("button", { name: /^disconnect$/i })).toBeInTheDocument();
	},
};
