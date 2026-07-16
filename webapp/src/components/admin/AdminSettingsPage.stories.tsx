import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, within } from "storybook/test";
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

/** Every feature off — the Features section still renders; only the league card is conditional. */
export const Default: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("heading", { name: /^features$/i })).toBeInTheDocument();
		await expect(canvas.queryByText(/reset and recalculate leagues/i)).not.toBeInTheDocument();
	},
};

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
