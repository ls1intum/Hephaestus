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
	practiceReviewAutoTriggerEnabled: false,
	practiceReviewManualTriggerEnabled: false,
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
			practiceReviewAutoTriggerEnabled: true,
			practiceReviewManualTriggerEnabled: true,
		},
	},
};

/** Mentor chat enabled only — proves the new toggle renders next to Practice Review. */
export const MentorEnabled: Story = {
	args: { values: { ...allOff, mentorEnabled: true } },
};

/** Practice Review enabled with both sub-triggers on — shows the nested toggle layout. */
export const PracticesWithBothTriggers: Story = {
	args: {
		values: {
			...allOff,
			practicesEnabled: true,
			practiceReviewAutoTriggerEnabled: true,
			practiceReviewManualTriggerEnabled: true,
		},
	},
};

/** Practice Review parent on, both children off — reviews can't fire (UI edge case). */
export const PracticesBothTriggersOff: Story = {
	args: { values: { ...allOff, practicesEnabled: true } },
};

/** Save in progress — every switch disabled. */
export const Saving: Story = {
	args: {
		values: {
			...allOff,
			practicesEnabled: true,
			mentorEnabled: true,
			practiceReviewAutoTriggerEnabled: true,
		},
		isSaving: true,
	},
};
