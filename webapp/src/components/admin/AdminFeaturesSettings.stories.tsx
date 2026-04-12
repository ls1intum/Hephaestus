import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { AdminFeaturesSettings } from "./AdminFeaturesSettings";

/**
 * Admin component for managing workspace feature flags.
 * Each toggle enables or disables a workspace feature.
 * Practice Review has sub-toggles for auto-trigger and manual trigger.
 */
const meta = {
	component: AdminFeaturesSettings,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	argTypes: {
		practicesEnabled: {
			control: "boolean",
			description: "Whether best practices feature is enabled",
		},
		achievementsEnabled: {
			control: "boolean",
			description: "Whether achievements feature is enabled",
		},
		leaderboardEnabled: {
			control: "boolean",
			description: "Whether leaderboard feature is enabled",
		},
		progressionEnabled: {
			control: "boolean",
			description: "Whether progression feature is enabled",
		},
		leaguesEnabled: {
			control: "boolean",
			description: "Whether leagues feature is enabled",
		},
		practiceReviewAutoTriggerEnabled: {
			control: "boolean",
			description: "Whether auto-triggered practice reviews are enabled",
		},
		practiceReviewManualTriggerEnabled: {
			control: "boolean",
			description: "Whether manual practice review trigger via bot command is enabled",
		},
		isSaving: {
			control: "boolean",
			description: "Whether a feature toggle is currently being saved",
		},
	},
	args: {
		practicesEnabled: false,
		achievementsEnabled: false,
		leaderboardEnabled: false,
		progressionEnabled: false,
		leaguesEnabled: false,
		practiceReviewAutoTriggerEnabled: true,
		practiceReviewManualTriggerEnabled: true,
		isSaving: false,
		onToggle: fn(),
	},
} satisfies Meta<typeof AdminFeaturesSettings>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Default state with all features disabled. Sub-toggles are hidden. */
export const Default: Story = {};

/** All features enabled, including sub-toggles visible. */
export const AllEnabled: Story = {
	args: {
		practicesEnabled: true,
		achievementsEnabled: true,
		leaderboardEnabled: true,
		progressionEnabled: true,
		leaguesEnabled: true,
		practiceReviewAutoTriggerEnabled: true,
		practiceReviewManualTriggerEnabled: true,
	},
};

/** Only practices enabled with both trigger modes active. */
export const PracticesOnly: Story = {
	args: {
		practicesEnabled: true,
	},
};

/** Practices enabled with only auto-trigger active. */
export const AutoTriggerOnly: Story = {
	args: {
		practicesEnabled: true,
		practiceReviewAutoTriggerEnabled: true,
		practiceReviewManualTriggerEnabled: false,
	},
};

/** Practices enabled with only manual trigger active. */
export const ManualTriggerOnly: Story = {
	args: {
		practicesEnabled: true,
		practiceReviewAutoTriggerEnabled: false,
		practiceReviewManualTriggerEnabled: true,
	},
};

/** Practices enabled but both triggers off — reviews cannot fire. */
export const BothTriggersOff: Story = {
	args: {
		practicesEnabled: true,
		practiceReviewAutoTriggerEnabled: false,
		practiceReviewManualTriggerEnabled: false,
	},
};

/** Gamification features only (leaderboard + achievements + progression). */
export const GamificationOnly: Story = {
	args: {
		achievementsEnabled: true,
		leaderboardEnabled: true,
		progressionEnabled: true,
	},
};

/** Saving state — all switches disabled. Sub-toggles visible and disabled. */
export const Saving: Story = {
	args: {
		practicesEnabled: true,
		achievementsEnabled: true,
		leaderboardEnabled: true,
		progressionEnabled: true,
		leaguesEnabled: true,
		isSaving: true,
	},
};
