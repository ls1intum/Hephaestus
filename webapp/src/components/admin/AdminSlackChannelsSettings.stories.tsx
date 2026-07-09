import type { Meta, StoryObj } from "@storybook/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { SlackMonitoredChannel } from "@/api/types.gen";
import { AdminSlackChannelsSettings } from "./AdminSlackChannelsSettings";

/**
 * Admin surface to allow-list Slack channels and drive their per-channel consent lifecycle.
 * Presentational: every mutation is delegated to the route container, so these stories mock
 * the callbacks with `fn()` and never touch the network. The history Sheet issues a lazy
 * query, so the component is wrapped in a fresh QueryClient (its query stays disabled while
 * the Sheet is closed).
 */
const meta = {
	component: AdminSlackChannelsSettings,
	parameters: { layout: "padded" },
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
		hasSlackConnection: true,
		isLoading: false,
		channels: [],
		onRegisterChannel: fn(),
		onUpdateConsent: fn(),
		onRemoveChannel: fn(),
	},
} satisfies Meta<typeof AdminSlackChannelsSettings>;

export default meta;
type Story = StoryObj<typeof meta>;

const iso = (days: number) => new Date(Date.now() - days * 24 * 60 * 60 * 1000);

const pending: SlackMonitoredChannel = {
	id: 1,
	slackChannelId: "C01PENDING01",
	slackTeamId: "T0000000000",
	channelName: "team-intro",
	consentState: "PENDING",
	optedOutMemberCount: 0,
	createdAt: iso(3),
};

const active: SlackMonitoredChannel = {
	id: 2,
	slackChannelId: "C02ACTIVE002",
	slackTeamId: "T0000000000",
	channelName: "team-standup",
	consentState: "ACTIVE",
	optedOutMemberCount: 2,
	consentAnnouncedAt: iso(2),
	createdAt: iso(5),
};

const paused: SlackMonitoredChannel = {
	id: 3,
	slackChannelId: "C03PAUSED003",
	slackTeamId: "T0000000000",
	channelName: "team-random",
	consentState: "PAUSED",
	optedOutMemberCount: 0,
	consentAnnouncedAt: iso(4),
	createdAt: iso(6),
};

const revoked: SlackMonitoredChannel = {
	id: 4,
	slackChannelId: "C04REVOKED04",
	slackTeamId: "T0000000000",
	channelName: "team-legacy",
	consentState: "REVOKED",
	optedOutMemberCount: 0,
	consentAnnouncedAt: iso(9),
	createdAt: iso(10),
};

/** Mixed list — one of each active lifecycle state. */
export const Default: Story = {
	args: { channels: [pending, active, paused] },
};

/** First run — no channels yet; the empty state offers an Add affordance. */
export const Empty: Story = {
	args: { channels: [] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/no channels monitored yet/i)).toBeInTheDocument();
	},
};

/** Every consent state visible at once — badge word+icon per state. */
export const AllStates: Story = {
	args: { channels: [pending, active, paused, revoked] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getAllByText("Not started").length).toBeGreaterThan(0);
		await expect(canvas.getByText("Monitoring")).toBeInTheDocument();
		await expect(canvas.getByText("Paused")).toBeInTheDocument();
		await expect(canvas.getByText("Revoked")).toBeInTheDocument();
	},
};

/** Opted-out members are surfaced as a count next to the channel. */
export const WithOptOuts: Story = {
	args: { channels: [active] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("2")).toBeInTheDocument();
	},
};

/** No channelName — the row falls back to the raw channel id. */
export const NoChannelName: Story = {
	args: {
		channels: [
			{
				id: 9,
				slackChannelId: "C09NONAMED09",
				slackTeamId: "T0000000000",
				consentState: "ACTIVE",
				optedOutMemberCount: 0,
				consentAnnouncedAt: iso(1),
				createdAt: iso(2),
			},
		],
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getAllByText("C09NONAMED09").length).toBeGreaterThan(0);
	},
};

/** Slack not connected — the Add-channel affordance is disabled. */
export const NotConnected: Story = {
	args: { hasSlackConnection: false, channels: [pending] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /add channel/i })).toBeDisabled();
	},
};

/** Activation is a deliberate confirm step — the row menu opens a consequences Dialog. */
export const ActivateConfirm: Story = {
	args: { channels: [pending] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /actions for team-intro/i }));
		await userEvent.click(await screen.findByRole("menuitem", { name: /activate monitoring/i }));
		const dialog = await screen.findByRole("dialog");
		await expect(within(dialog).getByText(/post a visible announcement/i)).toBeInTheDocument();
		await expect(
			within(dialog).getByRole("button", { name: /^activate monitoring$/i }),
		).toBeInTheDocument();
	},
};

/** Revoke is gated by a type-to-confirm AlertDialog. */
export const RevokeTypeToConfirm: Story = {
	args: { channels: [active] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /actions for team-standup/i }));
		await userEvent.click(await screen.findByRole("menuitem", { name: /remove & erase/i }));
		const dialog = await screen.findByRole("alertdialog");
		const confirm = within(dialog).getByRole("button", { name: /remove & erase/i });
		await expect(confirm).toBeDisabled();
		await userEvent.type(within(dialog).getByLabelText(/to confirm/i), active.slackChannelId);
		await expect(confirm).toBeEnabled();
	},
};

/** Loading — skeleton rows while the channel list resolves. */
export const Loading: Story = {
	args: { isLoading: true, channels: [] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/monitored channels have their/i)).toBeInTheDocument();
	},
};

/** The channel-list query failed — a distinct error panel with Retry, not the friendly empty state. */
export const LoadError: Story = {
	args: { channels: [], isError: true, onRetry: fn() },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.queryByText(/no channels monitored yet/i)).not.toBeInTheDocument();
		await expect(canvas.getByText(/couldn't load the monitored channels/i)).toBeInTheDocument();
		await expect(canvas.getByRole("button", { name: /^retry$/i })).toBeInTheDocument();
	},
};

/** Removing a PENDING channel that never got past setup: no type-to-confirm, accurate copy. */
export const RemovePendingNothingCollected: Story = {
	args: { channels: [pending] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /actions for team-intro/i }));
		await userEvent.click(await screen.findByRole("menuitem", { name: /remove & erase/i }));
		const dialog = await screen.findByRole("alertdialog");
		await expect(within(dialog).getByText(/nothing has been collected/i)).toBeInTheDocument();
		await expect(within(dialog).queryByLabelText(/to confirm/i)).not.toBeInTheDocument();
		await expect(within(dialog).getByRole("button", { name: /^remove$/i })).toBeEnabled();
	},
};

/** The searchable channel picker: search filters the list, and a selection registers without
 * manual id entry. */
export const AddChannelPicker: Story = {
	args: {
		channels: [],
		channelCandidates: [
			{
				slackChannelId: "C05GENERAL5",
				channelName: "general",
				privateChannel: false,
				member: true,
				archived: false,
			},
			{
				slackChannelId: "C06STANDUP6",
				channelName: "team-standup",
				privateChannel: true,
				member: true,
				archived: true,
			},
		],
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getAllByRole("button", { name: /add channel/i })[0]);
		const dialog = await screen.findByRole("dialog");

		// The archived channel is a disabled option with a reason, not silently missing from
		// the list.
		const archived = within(dialog).getByRole("option", { name: /#team-standup/i });
		await expect(archived).toHaveAttribute("aria-disabled", "true");
		await expect(within(dialog).getByText(/^archived$/i)).toBeInTheDocument();

		// Searching narrows the option list instead of scrolling a flat button list.
		await userEvent.type(
			within(dialog).getByRole("combobox", { name: /search available slack channels/i }),
			"general",
		);
		await expect(within(dialog).getByRole("option", { name: /#general/i })).toBeInTheDocument();
		await expect(
			within(dialog).queryByRole("option", { name: /#team-standup/i }),
		).not.toBeInTheDocument();

		await userEvent.click(within(dialog).getByRole("option", { name: /#general/i }));
		await userEvent.click(within(dialog).getByRole("button", { name: /^add channel$/i }));
	},
};

/** Mutation error — a rejected register keeps the dialog open so the admin can retry. */
export const MutationError: Story = {
	args: {
		channels: [],
		onRegisterChannel: fn(async () => {
			throw new Error("slack rejected the channel");
		}),
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// Empty list ⇒ both a header button and an empty-state CTA; open via the header one.
		await userEvent.click(canvas.getAllByRole("button", { name: /add channel/i })[0]);
		const dialog = await screen.findByRole("dialog");
		await userEvent.type(within(dialog).getByLabelText(/paste channel link or id/i), "C0974LJBPBK");
		await userEvent.click(within(dialog).getByRole("button", { name: /^add channel$/i }));
		// Rejected mutation ⇒ the dialog stays open for a retry.
		await expect(await screen.findByRole("dialog")).toBeInTheDocument();
	},
};
