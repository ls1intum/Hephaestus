import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { AdminFeaturesSettings, type FeatureValues } from "./AdminFeaturesSettings";

const allOff: FeatureValues = {
	practicesEnabled: false,
	mentorEnabled: false,
	achievementsEnabled: false,
	practiceReviewAutoTriggerEnabled: false,
	practiceReviewManualTriggerEnabled: false,
};

const meta = {
	component: AdminFeaturesSettings,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		values: allOff,
		healthVisibility: "MENTORS_ONLY",
		isSaving: false,
		onToggle: fn(),
		onHealthVisibilityChange: fn(),
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
			practiceReviewAutoTriggerEnabled: true,
			practiceReviewManualTriggerEnabled: true,
		},
	},
};

export const MentorEnabled: Story = {
	args: { values: { ...allOff, mentorEnabled: true } },
};

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

export const PracticesBothTriggersOff: Story = {
	args: { values: { ...allOff, practicesEnabled: true } },
};

export const MentorsOnlyVisibility: Story = {
	args: { healthVisibility: "MENTORS_ONLY" },
};

export const EveryoneVisibility: Story = {
	args: { healthVisibility: "EVERYONE" },
};

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
