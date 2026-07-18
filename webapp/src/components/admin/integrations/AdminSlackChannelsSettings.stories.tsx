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
		// Both the header button and the empty-state CTA are labelled "Add channel", so an empty
		// list must offer two of them — the CTA is not a relabelled header button.
		await expect(canvas.getAllByRole("button", { name: /add channel/i }).length).toBeGreaterThan(1);
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

/** Opted-out members are surfaced as a count per channel — including a visible 0. */
export const WithOptOuts: Story = {
	args: { channels: [pending, active] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// Scope each count to its own row (via the deterministic action-button label): a bare
		// getByText("2") would be satisfied by a stray "2" rendered anywhere in the table.
		const activeRow = canvas
			.getByRole("button", { name: "Actions for team-standup" })
			.closest("tr");
		const pendingRow = canvas.getByRole("button", { name: "Actions for team-intro" }).closest("tr");
		await expect(within(activeRow as HTMLElement).getByText("2")).toBeInTheDocument();
		// 0 is rendered as a trust signal rather than blanked out.
		await expect(within(pendingRow as HTMLElement).getByText("0")).toBeInTheDocument();
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

/**
 * Slack not connected — the section stays visible so an admin can discover what channel
 * monitoring does before installing the app, and points back at the connect card instead of
 * offering an Add button that could not work.
 */
export const NotConnected: Story = {
	args: { hasSlackConnection: false, channels: [pending] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.queryByRole("button", { name: /add channel/i })).not.toBeInTheDocument();
		await expect(canvas.getByText(/connect slack to monitor channels/i)).toBeInTheDocument();
		// The passed-in channel must not render while disconnected: the inert section shows only the
		// discovery copy, never a monitored-channel row the admin cannot act on.
		await expect(canvas.queryByText("team-intro")).not.toBeInTheDocument();
	},
};

/** Activation is a deliberate confirm step — the row menu opens a consequences Dialog. */
export const ActivateConfirm: Story = {
	args: { channels: [pending] },
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /actions for team-intro/i }));
		await userEvent.click(await screen.findByRole("menuitem", { name: /activate monitoring/i }));
		const dialog = await screen.findByRole("dialog");
		await expect(within(dialog).getByText(/post a visible announcement/i)).toBeInTheDocument();

		// Opening the dialog must not itself transition the channel — the gate is the confirm.
		await expect(args.onUpdateConsent).not.toHaveBeenCalled();

		await userEvent.click(within(dialog).getByRole("button", { name: /^activate monitoring$/i }));
		await expect(args.onUpdateConsent).toHaveBeenCalledWith({
			slackChannelId: pending.slackChannelId,
			consentState: "ACTIVE",
		});
	},
};

/** Revoke is gated by a type-to-confirm AlertDialog that validates on submit. */
export const RevokeTypeToConfirm: Story = {
	args: { channels: [active] },
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /actions for team-standup/i }));
		await userEvent.click(await screen.findByRole("menuitem", { name: /remove & erase/i }));
		const dialog = await screen.findByRole("alertdialog");

		// Enabled, but it will not erase anything until the ID matches — and it says so.
		const confirm = within(dialog).getByRole("button", { name: /remove & erase/i });
		await expect(confirm).toBeEnabled();
		await userEvent.click(confirm);
		await expect(within(dialog).getByText(/that does not match/i)).toBeInTheDocument();
		await expect(args.onRemoveChannel).not.toHaveBeenCalled();

		await userEvent.type(within(dialog).getByLabelText(/to confirm/i), active.slackChannelId);
		await expect(within(dialog).queryByText(/that does not match/i)).not.toBeInTheDocument();

		// Once the ID matches, the erase goes through and carries the typed reason.
		await userEvent.type(within(dialog).getByLabelText(/reason/i), "left the course");
		await userEvent.click(confirm);
		await expect(args.onRemoveChannel).toHaveBeenCalledWith({
			slackChannelId: active.slackChannelId,
			reason: "left the course",
		});
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
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.queryByText(/no channels monitored yet/i)).not.toBeInTheDocument();
		await expect(canvas.getByRole("alert")).toHaveTextContent(
			/couldn't load the monitored channels/i,
		);

		// Retry is wired, not decorative.
		await userEvent.click(canvas.getByRole("button", { name: /^retry$/i }));
		await expect(args.onRetry).toHaveBeenCalledTimes(1);
	},
};

/** Removing a PENDING channel that never got past setup: no type-to-confirm, accurate copy. */
export const RemovePendingNothingCollected: Story = {
	args: { channels: [pending] },
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /actions for team-intro/i }));
		await userEvent.click(await screen.findByRole("menuitem", { name: /remove & erase/i }));
		const dialog = await screen.findByRole("alertdialog");
		await expect(within(dialog).getByText(/nothing has been collected/i)).toBeInTheDocument();
		await expect(within(dialog).queryByLabelText(/to confirm/i)).not.toBeInTheDocument();

		// No gate to clear: Remove goes straight through, with no reason recorded.
		await userEvent.click(within(dialog).getByRole("button", { name: /^remove$/i }));
		await expect(args.onRemoveChannel).toHaveBeenCalledWith({
			slackChannelId: pending.slackChannelId,
			reason: undefined,
		});
	},
};

/**
 * The one channel control: a combobox. Search filters the list, disabled options keep a visible
 * reason, and a selection registers without the admin ever handling a raw channel id.
 */
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
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getAllByRole("button", { name: /add channel/i })[0]);
		const dialog = await screen.findByRole("dialog");

		// The options live in the combobox's popover — open it. (The popover is portalled, so
		// the options are queried from the document, not from the dialog subtree.)
		await userEvent.click(within(dialog).getByRole("combobox", { name: /^channel$/i }));

		// The archived channel is a disabled option with a reason, not silently missing.
		await expect(await screen.findByRole("option", { name: /#team-standup/i })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
		await expect(screen.getByText(/^archived$/i)).toBeInTheDocument();

		// Searching narrows the option list instead of scrolling a flat button list.
		await userEvent.type(
			screen.getByRole("combobox", { name: /search available slack channels/i }),
			"general",
		);
		await expect(screen.getByRole("option", { name: /#general/i })).toBeInTheDocument();
		await expect(screen.queryByRole("option", { name: /#team-standup/i })).not.toBeInTheDocument();

		// Picking an option and submitting registers the channel — the admin never handles a raw id.
		await userEvent.click(screen.getByRole("option", { name: /#general/i }));
		await userEvent.click(within(dialog).getByRole("button", { name: /^add channel$/i }));
		await expect(args.onRegisterChannel).toHaveBeenCalledWith({
			slackChannelId: "C05GENERAL5",
			channelName: "general",
		});
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
		await userEvent.type(
			within(dialog).getByLabelText(/paste a channel link or id/i),
			"C0974LJBPBK",
		);
		await userEvent.click(within(dialog).getByRole("button", { name: /^add channel$/i }));
		// Rejected mutation ⇒ the dialog stays open for a retry.
		await expect(await screen.findByRole("dialog")).toBeInTheDocument();
	},
};
