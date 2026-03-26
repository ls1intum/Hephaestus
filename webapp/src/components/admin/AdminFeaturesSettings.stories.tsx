import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { AdminFeaturesSettings } from "./AdminFeaturesSettings";

/**
 * Admin component for managing workspace feature flags.
 * Each toggle enables or disables a workspace feature.
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
			description: "Whether progression/leagues feature is enabled",
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
		isSaving: false,
		onToggle: fn(),
	},
} satisfies Meta<typeof AdminFeaturesSettings>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Default state with all features disabled */
export const Default: Story = {};

/** All features explicitly enabled */
export const AllEnabled: Story = {
	args: {
		practicesEnabled: true,
		achievementsEnabled: true,
		leaderboardEnabled: true,
		progressionEnabled: true,
	},
};

/** Only practices enabled, rest disabled */
export const PracticesOnly: Story = {
	args: {
		practicesEnabled: true,
		achievementsEnabled: false,
		leaderboardEnabled: false,
		progressionEnabled: false,
	},
};

/** Gamification features only (leaderboard + achievements + progression) */
export const GamificationOnly: Story = {
	args: {
		practicesEnabled: false,
		achievementsEnabled: true,
		leaderboardEnabled: true,
		progressionEnabled: true,
	},
};

/** Saving state with switches disabled */
export const Saving: Story = {
	args: {
		practicesEnabled: true,
		achievementsEnabled: true,
		leaderboardEnabled: true,
		progressionEnabled: true,
		isSaving: true,
	},
};
