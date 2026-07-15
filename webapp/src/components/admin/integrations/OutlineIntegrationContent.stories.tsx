import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { OutlineIntegrationContent } from "./OutlineIntegrationContent";

const meta = {
	component: OutlineIntegrationContent,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		connectCardProps: {
			connected: false,
			onConnect: fn(),
			onDisconnect: fn(),
			onSyncNow: fn(),
			onCancel: fn(),
		},
	},
} satisfies Meta<typeof OutlineIntegrationContent>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Disconnected: Story = {};
