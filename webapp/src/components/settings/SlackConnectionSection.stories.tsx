import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { SlackConnectionSection } from "./SlackConnectionSection";

const meta = {
	component: SlackConnectionSection,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		onDisconnect: fn(),
		onSync: fn(),
	},
	decorators: [
		(Story) => (
			<div className="w-[600px]">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof SlackConnectionSection>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Disconnected: Story = {
	args: {
		isConnected: false,
		slackEnabled: true,
		linkUrl: "https://keycloak.example.com/realms/hephaestus/account",
	},
};

export const Connected: Story = {
	args: {
		isConnected: true,
		slackUserId: "U12345ABCDE",
		slackEnabled: true,
	},
};

export const Loading: Story = {
	args: {
		isConnected: false,
		slackEnabled: true,
		isLoading: true,
	},
};

export const Disabled: Story = {
	args: {
		isConnected: false,
		slackEnabled: false,
	},
};
