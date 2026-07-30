import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { AdminFeaturesSettings, type FeatureValues } from "./AdminFeaturesSettings";

const allOff: FeatureValues = {
	practicesEnabled: false,
	mentorEnabled: false,
	achievementsEnabled: false,
	leaderboardEnabled: false,
	progressionEnabled: false,
	leaguesEnabled: false,
};

const meta = {
	component: AdminFeaturesSettings,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		values: allOff,
		isSaving: false,
		onToggle: fn(),
	},
} satisfies Meta<typeof AdminFeaturesSettings>;

export default meta;
type Story = StoryObj<typeof meta>;

export const AllDisabled: Story = {};

export const AllEnabled: Story = {
	args: {
		values: {
			...allOff,
			practicesEnabled: true,
			mentorEnabled: true,
			achievementsEnabled: true,
			leaderboardEnabled: true,
			progressionEnabled: true,
			leaguesEnabled: true,
		},
	},
};

export const Saving: Story = {
	args: {
		values: {
			...allOff,
			practicesEnabled: true,
			mentorEnabled: true,
		},
		isSaving: true,
	},
};
