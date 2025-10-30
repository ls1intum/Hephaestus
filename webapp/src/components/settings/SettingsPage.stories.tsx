import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { SettingsPage } from "./SettingsPage";

/**
 * SettingsPage component for the user settings page
 * Combines notification, research, and account management sections
 */
const meta = {
	component: SettingsPage,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	argTypes: {
		notificationsProps: {
			description: "Props for the NotificationsSection component",
		},
		researchProps: {
			description: "Props for the ResearchSection component",
		},
		accountProps: {
			description: "Props for the AccountSection component",
		},
		isLoading: {
			control: "boolean",
			description: "Whether the settings are still loading",
		},
	},
} satisfies Meta<typeof SettingsPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default view with notifications enabled and research not opted out
 */
export const Default: Story = {
	args: {
		notificationsProps: {
			receiveNotifications: true,
			onToggleNotifications: fn(),
		},
		researchProps: {
			researchOptOut: false,
			onToggleResearchOptOut: fn(),
		},
		accountProps: {
			onDeleteAccount: fn(),
		},
		isLoading: false,
	},
};

/**
 * View with notifications disabled
 */
export const NotificationsDisabled: Story = {
	args: {
		notificationsProps: {
			receiveNotifications: false,
			onToggleNotifications: fn(),
		},
		researchProps: {
			researchOptOut: false,
			onToggleResearchOptOut: fn(),
		},
		accountProps: {
			onDeleteAccount: fn(),
		},
		isLoading: false,
	},
};

/**
 * View with research opted out
 */
export const ResearchOptedOut: Story = {
	args: {
		notificationsProps: {
			receiveNotifications: true,
			onToggleNotifications: fn(),
		},
		researchProps: {
			researchOptOut: true,
			onToggleResearchOptOut: fn(),
		},
		accountProps: {
			onDeleteAccount: fn(),
		},
		isLoading: false,
	},
};

/**
 * Loading state while settings are being fetched
 */
export const Loading: Story = {
	args: {
		notificationsProps: {
			receiveNotifications: false,
			onToggleNotifications: fn(),
		},
		researchProps: {
			researchOptOut: false,
			onToggleResearchOptOut: fn(),
		},
		accountProps: {
			onDeleteAccount: fn(),
		},
		isLoading: true,
	},
};
