import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { NotificationsSection } from "./NotificationsSection";

/**
 * NotificationsSection component for managing notification preferences
 * Allows users to toggle email notifications
 */
const meta = {
	component: NotificationsSection,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	argTypes: {
		receiveNotifications: {
			control: "boolean",
			description: "Whether email notifications are enabled",
		},
		onToggleNotifications: {
			description: "Callback when notifications setting is changed",
		},
		isLoading: {
			control: "boolean",
			description: "Whether the component is in loading state",
		},
	},
	args: {
		onToggleNotifications: fn(),
	},
} satisfies Meta<typeof NotificationsSection>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default state with notifications enabled
 */
export const Enabled: Story = {
	args: {
		receiveNotifications: true,
		isLoading: false,
	},
};

/**
 * State with notifications disabled
 */
export const Disabled: Story = {
	args: {
		receiveNotifications: false,
		isLoading: false,
	},
};

/**
 * Loading state where the toggle is disabled
 */
export const Loading: Story = {
	args: {
		receiveNotifications: true,
		isLoading: true,
	},
};
