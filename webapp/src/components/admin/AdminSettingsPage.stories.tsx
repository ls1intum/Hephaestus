import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import type { FeatureValues } from "./AdminFeaturesSettings";
import { AdminSettingsPage } from "./AdminSettingsPage";

const allOff: FeatureValues = {
	practicesEnabled: false,
	mentorEnabled: false,
	achievementsEnabled: false,
	leaderboardEnabled: false,
	progressionEnabled: false,
	leaguesEnabled: false,
	practiceReviewAutoTriggerEnabled: true,
	practiceReviewManualTriggerEnabled: true,
};

const meta = {
	component: AdminSettingsPage,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		isResettingLeagues: false,
		onResetLeagues: fn(),
		features: allOff,
		isSavingFeatures: false,
		onToggleFeature: fn(),
	},
} satisfies Meta<typeof AdminSettingsPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const ResettingLeagues: Story = {
	args: { isResettingLeagues: true, features: { ...allOff, leaguesEnabled: true } },
};

export const PracticeReviewWithSubToggles: Story = {
	args: {
		features: {
			...allOff,
			practicesEnabled: true,
			practiceReviewAutoTriggerEnabled: true,
			practiceReviewManualTriggerEnabled: false,
		},
	},
};

export const LeaguesEnabled: Story = {
	args: { features: { ...allOff, leaguesEnabled: true } },
};
