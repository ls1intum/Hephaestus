import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { OutlineIntegrationCard } from "./OutlineIntegrationCard";

/**
 * Workspace-admin card for the Outline documentation integration. Pure presentation — connect and
 * disconnect are delegated to the container via callbacks, so every story is one immutable snapshot.
 */
const meta = {
	component: OutlineIntegrationCard,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		connected: false,
		onConnect: fn(),
		onDisconnect: fn(),
	},
} satisfies Meta<typeof OutlineIntegrationCard>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Cold start — the connect button is disabled until a URL + token are entered. */
export const Disconnected: Story = {
	args: { connected: false },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /connect outline/i })).toBeDisabled();
	},
};

/** Entering a token with the default https URL enables the connect action. */
export const DisconnectedReadyToConnect: Story = {
	args: { connected: false },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.type(canvas.getByLabelText(/api token/i), "ol_api_secret");
		await expect(canvas.getByRole("button", { name: /connect outline/i })).toBeEnabled();
	},
};

/** Edge: a non-https server URL surfaces the format error and keeps connect disabled. */
export const InvalidServerUrl: Story = {
	args: { connected: false },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const url = canvas.getByLabelText(/server url/i);
		await userEvent.clear(url);
		await userEvent.type(url, "ftp://internal");
		await expect(canvas.getByText(/enter an https:\/\/ url/i)).toBeVisible();
		await expect(canvas.getByRole("button", { name: /connect outline/i })).toBeDisabled();
	},
};

/** Connected — shows the linked instance and the disconnect affordance. */
export const Connected: Story = {
	args: { connected: true, connectionLabel: "Acme Wiki" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/outline connected/i)).toBeInTheDocument();
		await expect(canvas.getByText(/acme wiki/i)).toBeInTheDocument();
	},
};

/** Connected — opening the disconnect dialog surfaces the erase warning + destructive confirm. */
export const ConnectedDisconnectDialog: Story = {
	args: { connected: true, connectionLabel: "Acme Wiki" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /disconnect outline/i }));
		// AlertDialog renders in a portal — query the whole document.
		const dialog = await screen.findByRole("alertdialog", { name: /disconnect outline\?/i });
		await expect(
			within(dialog).getByText(/every mirrored document.*is\s+erased/i),
		).toBeInTheDocument();
		await expect(within(dialog).getByRole("button", { name: /^disconnect$/i })).toBeInTheDocument();
	},
};
