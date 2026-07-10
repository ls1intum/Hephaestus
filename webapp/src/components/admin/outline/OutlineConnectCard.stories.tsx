import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { OutlineConnectCard } from "./OutlineConnectCard";

/**
 * Workspace-admin card for the Outline documentation integration. Pure presentation — connect,
 * disconnect and sync-now are delegated to the container via callbacks, so every story is one
 * immutable snapshot.
 */
const meta = {
	component: OutlineConnectCard,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		connected: false,
		onConnect: fn(),
		onDisconnect: fn(),
		onSyncNow: fn(),
	},
} satisfies Meta<typeof OutlineConnectCard>;

export default meta;
type Story = StoryObj<typeof meta>;

const healthyStatus = {
	webhookRegistered: true,
	documentCount: 128,
	lastSyncedAt: new Date(Date.now() - 15 * 60 * 1000),
};

/** Cold start — the connect button is disabled until a URL + token are entered; no allow-list field. */
export const Disconnected: Story = {
	args: { connected: false },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /connect outline/i })).toBeDisabled();
		// The collection allow-list moved to the post-connect collections plane.
		await expect(canvas.queryByLabelText(/allow-list/i)).not.toBeInTheDocument();
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

/** Connect failed — the ProblemDetail message is anchored inline under the form. */
export const ConnectError: Story = {
	args: {
		connected: false,
		errorMessage: "The Outline API rejected the token (401).",
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/rejected the token/i)).toBeVisible();
	},
};

/** Connected and healthy — webhook live, document count and relative last sync. */
export const Connected: Story = {
	args: {
		connected: true,
		connectionLabel: "Acme Wiki",
		status: healthyStatus,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/outline connected/i)).toBeInTheDocument();
		await expect(canvas.getByText(/acme wiki/i)).toBeInTheDocument();
		await expect(canvas.getByText(/live updates via webhook/i)).toBeInTheDocument();
		await expect(canvas.getByText(/128 documents mirrored/i)).toBeInTheDocument();
		await expect(canvas.getByText(/last synced/i)).toBeInTheDocument();
		await expect(canvas.getByRole("button", { name: /sync now/i })).toBeEnabled();
	},
};

/** Degraded: the webhook subscription is missing — updates arrive by polling only. */
export const ConnectedPollingOnly: Story = {
	args: {
		connected: true,
		connectionLabel: "Acme Wiki",
		status: { webhookRegistered: false, documentCount: 0 },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/polling only/i)).toBeInTheDocument();
		await expect(canvas.getByText(/not synced yet/i)).toBeInTheDocument();
	},
};

/** A full reconcile has just been requested — the Sync-now action is held down. */
export const Syncing: Story = {
	args: {
		connected: true,
		connectionLabel: "Acme Wiki",
		status: healthyStatus,
		isSyncing: true,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /starting sync/i })).toBeDisabled();
	},
};

/** Connected — opening the disconnect dialog surfaces the erase warning + destructive confirm. */
export const ConnectedDisconnectDialog: Story = {
	args: { connected: true, connectionLabel: "Acme Wiki", status: healthyStatus },
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
