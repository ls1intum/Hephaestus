import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { SlackPreferencesSection } from "./SlackPreferencesSection";

const meta = {
	component: SlackPreferencesSection,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	args: {
		onConnectSlack: fn(),
		onToggleChannelMessages: fn(),
	},
} satisfies Meta<typeof SlackPreferencesSection>;

export default meta;
type Story = StoryObj<typeof meta>;

const workspace = {
	workspaceSlug: "hephaestustest",
	workspaceName: "Hephaestus Test",
	slackTeamId: "T1",
	slackTeamName: "hephaestus-test",
	slackUserId: "U1",
	slackDisplayName: "Felix",
	channelMessagesAllowed: true,
	activeMonitoredChannelCount: 2,
};

export const Linked: Story = {
	args: {
		workspaces: [workspace],
		isSlackLinked: true,
		canConnectSlack: true,
	},
};

export const NotLinked: Story = {
	args: {
		workspaces: [],
		isSlackLinked: false,
		canConnectSlack: true,
	},
};

export const ConnectedWithoutWorkspace: Story = {
	args: {
		workspaces: [],
		isSlackLinked: true,
		canConnectSlack: true,
	},
};

export const Loading: Story = {
	args: {
		workspaces: [],
		isSlackLinked: true,
		canConnectSlack: true,
		isLoading: true,
	},
};
