import type { Meta, StoryObj } from "@storybook/react";
import { AchievementProgressDisplay } from "@/components/achievements/AchievementProgressDisplay";
import {
	apolloClarity,
	artemisHunt,
	asUI,
	athenaReview,
	dionysusDeploy,
	hermesSprint,
	prometheusLongName,
} from "@/components/achievements/storyMockData";

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
		achievement: asUI(apolloClarity),
	},
};

/**
 * Linear achievement that has reached its full potential.
 */
export const LinearCompleted: Story = {
	args: {
		achievement: asUI(athenaReview),
	},
};

/**
 * Binary achievement that remains locked behind the vault of the gods.
 */
export const BinaryLockedMilestone: Story = {
	args: {
		achievement: asUI(dionysusDeploy),
	},
};

/**
 * Binary achievement that has been unlocked through divine intervention.
 */
export const BinaryUnlockedMilestone: Story = {
	args: {
		achievement: asUI(hermesSprint),
	},
};

export const ZeroProgress: Story = {
	args: {
		achievement: asUI(artemisHunt),
	},
};

export const LongTextOverflow: Story = {
	args: {
		achievement: asUI(prometheusLongName),
	},
};
