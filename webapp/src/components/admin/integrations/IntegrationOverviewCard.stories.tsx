import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { IntegrationOverviewCard } from "./IntegrationOverviewCard";

const meta = {
	component: IntegrationOverviewCard,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		entry: {
			kind: "GITHUB",
			displayName: "GitHub",
			connected: true,
			connectionId: 7,
			connectionState: "ACTIVE",
		},
		status: {
			connectionId: 7,
			connectionState: "ACTIVE",
			kind: "GITHUB",
			health: "HEALTHY",
			resourceCounts: { total: 12, errored: 0 },
			lastSuccessfulSyncAt: new Date("2026-07-14T10:00:00Z"),
		},
		onSync: fn(),
	},
} satisfies Meta<typeof IntegrationOverviewCard>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Connected: Story = {};
export const Disconnected: Story = {
	args: {
		entry: { kind: "OUTLINE", displayName: "Outline", connected: false },
		status: undefined,
	},
};
export const Loading: Story = { args: { status: undefined, isStatusLoading: true } };
