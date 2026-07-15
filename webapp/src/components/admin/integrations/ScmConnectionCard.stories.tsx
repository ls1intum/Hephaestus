import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { ScmConnectionCard } from "./ScmConnectionCard";

const meta = {
	component: ScmConnectionCard,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		provider: "GITHUB",
		label: "GitHub",
		status: {
			connectionId: 7,
			connectionState: "ACTIVE",
			kind: "GITHUB",
			health: "HEALTHY",
			resourceCounts: { total: 12, errored: 0 },
			lastSuccessfulSyncAt: new Date("2026-07-14T10:00:00Z"),
		},
		isLoading: false,
		isConnectionActive: true,
		isAppInstallationWorkspace: false,
		isTriggering: false,
		isCancelling: false,
		onRetry: fn(),
		onSync: fn(),
		onBackfill: fn(),
		onCancel: fn(),
	},
} satisfies Meta<typeof ScmConnectionCard>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Connected: Story = {};
export const Loading: Story = { args: { status: undefined, isLoading: true } };
export const Missing: Story = { args: { status: undefined, isConnectionActive: false } };
