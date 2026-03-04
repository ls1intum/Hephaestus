import type { Meta, StoryObj } from "@storybook/react";
import { AchievementsListView } from "./AchievementsListView";
import { mythicAchievements, zeusThunderbolt } from "./storyMockData";

/**
 * Tabular view for displaying achievement progression in an accessible format.
 * Groups achievements by their divine category and shows status and progress bars.
 */
const meta: Meta<typeof AchievementsListView> = {
	component: AchievementsListView,
	parameters: {
		layout: "fullscreen",
		docs: {
			description: {
				component:
					"Displays achievements in a structured list/table format with digital mythological themes.",
			},
		},
	},
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<div className="bg-background p-8 min-h-screen">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof AchievementsListView>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Complete list showing mixed statuses across all categories.
 */
export const CompleteList: Story = {
	args: {
		achievements: mythicAchievements,
	},
};

/**
 * Filtered list showing only unlocked achievements.
 */
export const UnlockedOnly: Story = {
	args: {
		achievements: mythicAchievements.filter((a) => a.status === "unlocked"),
	},
};

/**
 * List focused on a single divine category.
 */
export const CategorySpecific: Story = {
	args: {
		achievements: mythicAchievements.filter((a) => a.category === "pull_requests"),
	},
};

/**
 * Empty list display when no achievements are loaded or available.
 */
export const EmptyListState: Story = {
	args: {
		achievements: [],
	},
};

/**
 * List with a single achievement to showcase row structure.
 */
export const SingleRowExample: Story = {
	args: {
		achievements: [zeusThunderbolt],
	},
};
