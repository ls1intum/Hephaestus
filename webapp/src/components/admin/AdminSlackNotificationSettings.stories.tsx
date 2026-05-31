import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { AdminSlackNotificationSettings } from "./AdminSlackNotificationSettings";

const meta = {
	component: AdminSlackNotificationSettings,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo-workspace",
		hasSlackConnection: false,
		enabled: false,
		scheduleDay: 1,
		scheduleTime: "09:00",
		onSaved: fn(),
	},
} satisfies Meta<typeof AdminSlackNotificationSettings>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Cold start — admin hasn't connected the workspace yet. */
export const NotConnected: Story = {
	args: { hasSlackConnection: false },
};

/** OAuth completed but the admin hasn't picked a channel yet. */
export const ConnectedNoChannel: Story = {
	args: { hasSlackConnection: true },
};

/** Fully configured: connected, channel + team filter set, digest enabled. */
export const ConnectedConfigured: Story = {
	args: {
		hasSlackConnection: true,
		channelId: "C0974LJBPBK",
		teamLabel: "engineering",
		enabled: true,
	},
};

/** Configured but admin has muted the weekly digest. */
export const ConnectedDisabled: Story = {
	args: {
		hasSlackConnection: true,
		channelId: "C0974LJBPBK",
		teamLabel: "engineering",
		enabled: false,
	},
};

/** Non-default day — pins that the day Select renders the label ("Thursday"), not the raw value. */
export const NonDefaultDay: Story = {
	args: {
		hasSlackConnection: true,
		channelId: "C0974LJBPBK",
		enabled: true,
		scheduleDay: 4,
		scheduleTime: "14:30",
	},
};

/** Invalid channel id — the field shows aria-invalid + the format error, and Save is disabled. */
export const InvalidChannel: Story = {
	args: {
		hasSlackConnection: true,
		channelId: "not-a-channel",
		enabled: true,
	},
};

/** Invalid time — the time field surfaces its HH:mm error and Save is disabled. */
export const InvalidTime: Story = {
	args: {
		hasSlackConnection: true,
		channelId: "C0974LJBPBK",
		enabled: true,
		scheduleTime: "9am",
	},
};
