import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { SlackSyncStatusCard } from "./SlackSyncStatusCard";

const meta = {
	component: SlackSyncStatusCard,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		status: {
			connectionId: 8,
			connectionState: "ACTIVE",
			kind: "SLACK",
			health: "HEALTHY",
			resourceCounts: { total: 3, errored: 0 },
			lastSuccessfulSyncAt: new Date("2026-07-14T10:00:00Z"),
		},
		isConnectionActive: true,
		isTriggering: false,
		isCancelling: false,
		onSync: fn(),
		onCancel: fn(),
	},
} satisfies Meta<typeof SlackSyncStatusCard>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Connected: Story = {};
export const Suspended: Story = { args: { isConnectionActive: false } };
