import type { Meta, StoryObj } from "@storybook/react";
import { AchievementProgressDisplay } from "./AchievementProgressDisplay";
import { apolloClarity, athenaReview, dionysusDeploy, hermesSprint } from "./storyMockData";

/**
 * Component for showcasing achievement progress indicators.
 * Supports both linear progress bars (for quantitative goals) and
 * binary status icons (for boolean milestones) within digital mythological contexts.
 */
const meta = {
	component: AchievementProgressDisplay,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"Displays progress for divine achievements using the standardized forged artifacts design system.",
			},
		},
	},
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<div className="bg-background p-12 min-w-[300px] border rounded-lg flex justify-center items-center">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof AchievementProgressDisplay>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Linear achievement showing partial progress toward a divine goal.
 */
export const LinearPartialProgress: Story = {
	args: {
		achievement: apolloClarity,
	},
};

/**
 * Linear achievement that has reached its full potential.
 */
export const LinearCompleted: Story = {
	args: {
		achievement: athenaReview,
	},
};

/**
 * Binary achievement that remains locked behind the vault of the gods.
 */
export const BinaryLockedMilestone: Story = {
	args: {
		achievement: dionysusDeploy,
	},
};

/**
 * Binary achievement that has been unlocked through divine intervention.
 */
export const BinaryUnlockedmilestone: Story = {
	args: {
		achievement: hermesSprint,
	},
};
