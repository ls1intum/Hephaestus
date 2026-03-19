import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { SettingsPage } from "./SettingsPage";

/**
 * SettingsPage component for the user settings page
 * Combines notification, AI review, research, and account management sections
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
		aiReviewProps: {
			description: "Props for the AiReviewSection component",
		},
		showAiReviewSection: {
			control: "boolean",
			description: "Whether to show the AI review section (feature-flagged)",
		},
		showResearchSection: {
			control: "boolean",
			description: "Whether to show the research participation section",
		},
		researchProps: {
			description: "Props for the ResearchParticipationSection component",
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
 * Default view with all settings enabled
 */
export const Default: Story = {
	args: {
		notificationsProps: {
			receiveNotifications: true,
			onToggleNotifications: fn(),
		},
		aiReviewProps: {
			aiReviewEnabled: true,
			onToggleAiReview: fn(),
		},
		showAiReviewSection: true,
		showResearchSection: true,
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
 * View with notifications and AI review disabled
 */
export const NotificationsDisabled: Story = {
	args: {
		notificationsProps: {
			receiveNotifications: false,
			onToggleNotifications: fn(),
		},
		aiReviewProps: {
			aiReviewEnabled: false,
			onToggleAiReview: fn(),
		},
		showAiReviewSection: true,
		showResearchSection: true,
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
		aiReviewProps: {
			aiReviewEnabled: true,
			onToggleAiReview: fn(),
		},
		showAiReviewSection: true,
		showResearchSection: true,
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

/**
 * View without AI review section (user lacks run_practice_review role)
 */
export const AiReviewHidden: Story = {
	args: {
		notificationsProps: {
			receiveNotifications: true,
			onToggleNotifications: fn(),
		},
		aiReviewProps: {
			aiReviewEnabled: true,
			onToggleAiReview: fn(),
		},
		showAiReviewSection: false,
		showResearchSection: true,
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
 * View without research section (PostHog not configured)
 */
export const ResearchHidden: Story = {
	args: {
		notificationsProps: {
			receiveNotifications: true,
			onToggleNotifications: fn(),
		},
		aiReviewProps: {
			aiReviewEnabled: true,
			onToggleAiReview: fn(),
		},
		showAiReviewSection: true,
		showResearchSection: false,
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
