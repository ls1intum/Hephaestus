import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { SettingsPage } from "./SettingsPage";

/**
 * SettingsPage component for the user settings page
 * Combines notification and account management sections
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
		accountProps: {
			description: "Props for the AccountSection component",
		},
		researchProps: {
			description: "Props for the ResearchParticipationSection component",
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
 * Default view with notifications enabled
 */
export const Default: Story = {
	args: {
		notificationsProps: {
			receiveNotifications: true,
			onToggleNotifications: fn(),
		},
		researchProps: {
			participateInResearch: true,
			onToggleResearch: fn(),
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
			participateInResearch: false,
			onToggleResearch: fn(),
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
			participateInResearch: true,
			onToggleResearch: fn(),
		},
		accountProps: {
			onDeleteAccount: fn(),
		},
		isLoading: true,
	},
};
