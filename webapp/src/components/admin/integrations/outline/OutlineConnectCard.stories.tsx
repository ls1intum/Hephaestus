import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { OutlineConnectCard } from "./OutlineConnectCard";

/**
 * Outline's token-paste lifecycle card — the one piece with no SCM/Slack analogue. When disconnected
 * it is the connect form (server URL + API token); when connected it names the linked instance and
 * shows the stored token's health plus a guarded disconnect. The connection plane (health, freshness,
 * diagnostics, Sync/Cancel) lives in the shared `SyncStatusHeader` above this card on the real page, so
 * every story here is one immutable snapshot of connect / token / disconnect only.
 */
const meta = {
	component: OutlineConnectCard,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		connected: false,
		onConnect: fn(),
		onDisconnect: fn(),
	},
} satisfies Meta<typeof OutlineConnectCard>;

export default meta;
type Story = StoryObj<typeof meta>;

const inDays = (days: number) => new Date(Date.now() + days * 24 * 60 * 60 * 1000);
const daysAgo = (days: number) => new Date(Date.now() - days * 24 * 60 * 60 * 1000);

/** A token Outline accepts, whose key metadata it also lets us read. */
const healthyToken = {
	accepted: true,
	name: "Hephaestus mirror",
	last4: "9f2c",
	lastActiveAt: daysAgo(1),
	expiresAt: inDays(120),
};

/** Cold start — no prefilled server URL (a prefill would ship a self-host token to Outline Cloud). */
export const Disconnected: Story = {
	args: { connected: false },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByLabelText(/server url/i)).toHaveValue("");
		await expect(canvas.getByRole("button", { name: /connect outline/i })).toBeDisabled();
	},
};

/** Both fields have to be filled — the token alone leaves connect disabled. */
export const DisconnectedReadyToConnect: Story = {
	args: { connected: false },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.type(canvas.getByLabelText(/api token/i), "ol_api_secret");
		await expect(canvas.getByRole("button", { name: /connect outline/i })).toBeDisabled();

		await userEvent.type(canvas.getByLabelText(/server url/i), "https://wiki.acme.dev");
		await expect(canvas.getByRole("button", { name: /connect outline/i })).toBeEnabled();
	},
};

/** Edge: a non-https server URL surfaces the format error and keeps connect disabled. */
export const InvalidServerUrl: Story = {
	args: { connected: false },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.type(canvas.getByLabelText(/server url/i), "ftp://internal");
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

/**
 * The instance has no Outline integration enabled — the initiate 400 (no ConnectionStrategy for the
 * kind) is turned into a clear "not available here" hint so the admin does not keep retrying a
 * connect that can never succeed. The raw ProblemDetail stays visible above it.
 */
export const ConnectUnavailable: Story = {
	args: {
		connected: false,
		errorMessage: "No ConnectionStrategy registered for kind=OUTLINE",
		connectUnavailable: true,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/no connectionstrategy registered/i)).toBeVisible();
		await expect(canvas.getByText(/outline may not be enabled on this instance/i)).toBeVisible();
		await expect(canvas.getByText(/ask your server administrator/i)).toBeVisible();
	},
};

/**
 * Connected and healthy — the linked instance is named and the stored token is accepted, with its
 * metadata and expiry. Health/freshness/webhook and the Sync controls are not here: they live in the
 * shared `SyncStatusHeader` above this card on the page.
 */
export const Connected: Story = {
	args: {
		connected: true,
		connectionLabel: "Acme Wiki",
		tokenStatus: healthyToken,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/outline connected — acme wiki/i)).toBeInTheDocument();
		await expect(canvas.getByText(/outline accepts this token/i)).toBeInTheDocument();
		await expect(canvas.getByText(/hephaestus mirror/i)).toBeInTheDocument();
		await expect(canvas.getByText(/…9f2c/)).toBeInTheDocument();
		await expect(canvas.getByText(/expires in \d+ days \(on /i)).toBeInTheDocument();
		await expect(canvas.getByRole("button", { name: /disconnect outline/i })).toBeEnabled();
		// The connection plane's Sync control is not in this card.
		await expect(canvas.queryByRole("button", { name: /sync now/i })).not.toBeInTheDocument();
	},
};

/** A key created without an expiry — nothing to warn about, so it stays a muted fact. */
export const TokenNeverExpires: Story = {
	args: {
		connected: true,
		connectionLabel: "Acme Wiki",
		tokenStatus: { accepted: true, name: "Hephaestus mirror", last4: "9f2c" },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/never expires/i)).toBeInTheDocument();
		await expect(canvas.queryByText(/cannot be rotated/i)).not.toBeInTheDocument();
	},
};

/**
 * Inside the 14-day window: Outline keys cannot be rotated through the API, so the only fix is a
 * human creating a fresh key and re-entering it. The UI says exactly that.
 */
export const TokenExpiringSoon: Story = {
	args: {
		connected: true,
		connectionLabel: "Acme Wiki",
		tokenStatus: { ...healthyToken, expiresAt: inDays(5) },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/this api key expires in [45] days/i)).toBeInTheDocument();
		await expect(canvas.getByText(/cannot be rotated through the api/i)).toBeInTheDocument();
		await expect(canvas.getByText(/settings → api keys/i)).toBeInTheDocument();
	},
};

/** Outline rejects the stored token — the mirror is dead until an admin reconnects with a new key. */
export const TokenRejected: Story = {
	args: {
		connected: true,
		connectionLabel: "Acme Wiki",
		tokenStatus: { accepted: false },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			canvas.getByText(/outline no longer accepts this token — reconnect with a new one/i),
		).toBeInTheDocument();
		await expect(canvas.queryByText(/expires in/i)).not.toBeInTheDocument();
	},
};

/**
 * A scoped key cannot list itself, so Outline accepts it while telling us nothing about it —
 * we claim only what we know, and never guess an expiry.
 */
export const TokenMetadataUnavailable: Story = {
	args: {
		connected: true,
		connectionLabel: "Acme Wiki",
		tokenStatus: { accepted: true },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/outline accepts this token/i)).toBeInTheDocument();
		await expect(canvas.queryByText(/never expires/i)).not.toBeInTheDocument();
		await expect(canvas.queryByText(/expires in/i)).not.toBeInTheDocument();
		await expect(canvas.queryByText(/last used/i)).not.toBeInTheDocument();
	},
};

/** Connected — opening the disconnect dialog surfaces the erase warning + destructive confirm. */
export const ConnectedDisconnectDialog: Story = {
	args: {
		connected: true,
		connectionLabel: "Acme Wiki",
		tokenStatus: healthyToken,
	},
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

/**
 * The provider suspended the connection. The identity line lowercases nothing — it reads the wire enum
 * through `CONNECTION_STATE_LABEL` and drops the green check (spent only on ACTIVE). The consequence
 * ("Syncing is paused…") is explained by the shared `ConnectionStateNotice` above this card on the page.
 */
export const ConnectedButSuspended: Story = {
	args: {
		connected: true,
		connectionState: "SUSPENDED",
		connectionLabel: "Acme Wiki",
		tokenStatus: healthyToken,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/outline suspended — acme wiki/i)).toBeInTheDocument();
		// The token panel still reports the stored key even while syncing is paused.
		await expect(canvas.getByText(/outline accepts this token/i)).toBeInTheDocument();
	},
};

/** Setup hasn't finished — the identity line states PENDING plainly, since it resolves on its own. */
export const ConnectedButPending: Story = {
	args: {
		connected: true,
		connectionState: "PENDING",
		connectionLabel: "Acme Wiki",
		tokenStatus: healthyToken,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/outline finishing setup — acme wiki/i)).toBeInTheDocument();
	},
};

/** ACTIVE keeps the green check and names the linked instance — the steady state stays quiet. */
export const ConnectedActiveState: Story = {
	args: {
		connected: true,
		connectionState: "ACTIVE",
		connectionLabel: "Acme Wiki",
		tokenStatus: healthyToken,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/outline connected — acme wiki/i)).toBeInTheDocument();
	},
};
