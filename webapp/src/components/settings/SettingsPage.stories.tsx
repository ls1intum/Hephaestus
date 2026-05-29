import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { SettingsPage } from "./SettingsPage";

/**
 * SettingsPage component for the user settings page
 * Combines AI review, research, and account management sections
 */
const meta = {
	component: SettingsPage,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	argTypes: {
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
		onAccountDeleted: {
			description: "Called after the account is deleted (logout + redirect)",
		},
		isLoading: {
			control: "boolean",
			description: "Whether the settings are still loading",
		},
	},
} satisfies Meta<typeof SettingsPage>;

export default meta;
type Story = StoryObj<typeof meta>;

const defaultLinkedAccountsProps = {
	identities: [
		{
			id: 1,
			providerType: "GITHUB",
			username: "octocat",
			displayName: "The Octocat",
			lastLoginAt: new Date("2026-05-20T10:00:00Z"),
		},
	],
	providers: [
		{ registrationId: "github", displayName: "GitHub", providerType: "GITHUB" },
		{ registrationId: "gitlab-lrz", displayName: "GitLab LRZ", providerType: "GITLAB" },
	],
	onLink: fn(),
};

/**
 * Default view with all settings enabled
 */
export const Default: Story = {
	args: {
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
		linkedAccountsProps: defaultLinkedAccountsProps,
		onAccountDeleted: fn(),
		isLoading: false,
	},
};

/**
 * View with all toggles disabled
 */
export const AllTogglesDisabled: Story = {
	args: {
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
		linkedAccountsProps: defaultLinkedAccountsProps,
		onAccountDeleted: fn(),
		isLoading: false,
	},
};

/**
 * Loading state while settings are being fetched
 */
export const Loading: Story = {
	args: {
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
		linkedAccountsProps: defaultLinkedAccountsProps,
		onAccountDeleted: fn(),
		isLoading: true,
	},
};

/**
 * View without AI review section (user lacks run_practice_review role)
 */
export const AiReviewHidden: Story = {
	args: {
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
		linkedAccountsProps: defaultLinkedAccountsProps,
		onAccountDeleted: fn(),
		isLoading: false,
	},
};

/**
 * View without research section (PostHog not configured)
 */
export const ResearchHidden: Story = {
	args: {
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
		linkedAccountsProps: defaultLinkedAccountsProps,
		onAccountDeleted: fn(),
		isLoading: false,
	},
};
