import type { Meta, StoryObj } from "@storybook/react";
import { AchievementsListView } from "@/components/achievements/AchievementsListView";
import {
	asUI,
	mythicAchievementsUI,
	zeusThunderbolt,
} from "@/components/achievements/story-mock-data";

const meta = {
	component: AchievementsListView,
	parameters: {
		layout: "fullscreen",
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

export const CompleteList: Story = {
	args: {
		achievements: mythicAchievementsUI,
	},
};

export const UnlockedOnly: Story = {
	args: {
		achievements: mythicAchievementsUI.filter((a) => a.status === "unlocked"),
	},
};

export const CategorySpecific: Story = {
	args: {
		achievements: mythicAchievementsUI.filter((a) => a.category === "pull_requests"),
	},
};

export const EmptyListState: Story = {
	args: {
		achievements: [],
	},
};

export const SingleRowExample: Story = {
	args: {
		achievements: [asUI(zeusThunderbolt)],
	},
};
