import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { SlackMonitoredChannel } from "@/api/types.gen";
import { Table, TableBody, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { SlackChannelRow } from "./SlackChannelRow";

const iso = (days: number) => new Date(Date.now() - days * 24 * 60 * 60 * 1000);

const channel: SlackMonitoredChannel = {
	id: 1,
	slackTeamId: "T0000000000",
	slackChannelId: "C01ACTIVE001",
	channelName: "team-standup",
	consentState: "ACTIVE",
	optedOutMemberCount: 0,
	consentAnnouncedAt: iso(2),
	createdAt: iso(5),
};

/**
 * One allow-listed Slack channel. The consent state is rendered through the shared
 * `ConsentStateBadge`, so the row and the audit trail in the history sheet can never disagree
 * about what a state is called. Each state exposes only the transitions that are legal from it.
 */
const meta = {
	component: SlackChannelRow,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<Table>
				<TableHeader>
					<TableRow>
						<TableHead>Channel</TableHead>
						<TableHead>Status</TableHead>
						<TableHead>Opted out</TableHead>
						<TableHead>Announced</TableHead>
						<TableHead className="w-0 text-right">
							<span className="sr-only">Actions</span>
						</TableHead>
					</TableRow>
				</TableHeader>
				<TableBody>
					<Story />
				</TableBody>
			</Table>
		),
	],
	args: {
		channel,
		onActivate: fn(),
		onPause: fn(),
		onResume: fn(),
		onRemove: fn(),
		onSetUpAgain: fn(),
		onViewHistory: fn(),
	},
} satisfies Meta<typeof SlackChannelRow>;

export default meta;
type Story = StoryObj<typeof meta>;

/** PENDING — allow-listed but nothing is read yet; the only lifecycle action is Activate. */
export const NotStarted: Story = {
	args: {
		channel: {
			...channel,
			slackChannelId: "C02PENDING02",
			channelName: "team-intro",
			consentState: "PENDING",
			consentAnnouncedAt: undefined,
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Not started")).toBeInTheDocument();

		await userEvent.click(canvas.getByRole("button", { name: /actions for team-intro/i }));
		await expect(
			await screen.findByRole("menuitem", { name: /activate monitoring/i }),
		).toBeInTheDocument();
		await expect(screen.queryByRole("menuitem", { name: /^pause$/i })).not.toBeInTheDocument();
	},
};

/** ACTIVE — messages are being read; the row offers Pause, history and the destructive erase. */
export const Monitoring: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Monitoring")).toBeInTheDocument();

		await userEvent.click(canvas.getByRole("button", { name: /actions for team-standup/i }));
		await expect(await screen.findByRole("menuitem", { name: /^pause$/i })).toBeInTheDocument();
		await expect(
			screen.queryByRole("menuitem", { name: /activate monitoring/i }),
		).not.toBeInTheDocument();
	},
};

/** PAUSED — reading stopped, collected data kept; Resume is the way back. */
export const Paused: Story = {
	args: { channel: { ...channel, consentState: "PAUSED" } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Paused")).toBeInTheDocument();

		await userEvent.click(canvas.getByRole("button", { name: /actions for team-standup/i }));
		await expect(await screen.findByRole("menuitem", { name: /^resume$/i })).toBeInTheDocument();
	},
};

/** REVOKED — terminal. Nothing is left to erase, so only history and a fresh setup remain. */
export const Revoked: Story = {
	args: { channel: { ...channel, consentState: "REVOKED" } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Revoked")).toBeInTheDocument();

		await userEvent.click(canvas.getByRole("button", { name: /actions for team-standup/i }));
		await expect(
			await screen.findByRole("menuitem", { name: /set up again/i }),
		).toBeInTheDocument();
		await expect(
			screen.queryByRole("menuitem", { name: /remove & erase/i }),
		).not.toBeInTheDocument();
	},
};

/** Members who opted out are a first-class trust signal, with a keyboard-reachable tooltip. */
export const WithOptOuts: Story = {
	args: { channel: { ...channel, optedOutMemberCount: 3 } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: "3" })).toBeInTheDocument();
	},
};

/** No name from Slack — the row falls back to the stable id rather than rendering "#undefined". */
export const NoChannelName: Story = {
	args: { channel: { ...channel, channelName: undefined } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getAllByText(/C01ACTIVE001/).length).toBeGreaterThan(0);
	},
};
