import type { Meta, StoryObj } from "@storybook/react";
import { ReactFlowProvider } from "@xyflow/react";
import { AchievementSidebar } from "./AchievementSidebar";
import { mockUser, mythicAchievements } from "./storyMockData";

/**
 * The consolidated achievements sidebar showing stats, progress, recent unlocks,
 * and navigation controls. Used as a persistent right-hand companion on the skill tree page.
 */
const meta: Meta<typeof AchievementSidebar> = {
	component: AchievementSidebar,
	parameters: {
		layout: "fullscreen",
		docs: {
			description: {
				component:
					"A non-foldable sidebar that displays achievement statistics, overall progress, and recent unlocks with digital mythological themes.",
			},
		},
	},
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<ReactFlowProvider>
				<div className="h-screen w-full flex justify-end bg-background overflow-hidden relative">
					{/* Simulated main content area */}
					<div className="flex-1 bg-[radial-gradient(ellipse_at_center,var(--tw-gradient-stops))] from-primary/5 via-background to-background" />
					<Story />
				</div>
			</ReactFlowProvider>
		),
	],
};

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Standard view for the current user's profile.
 */
export const Default: Story = {
	args: {
		viewMode: "tree",
		onViewModeChange: () => {},
		isLoading: false,
		isError: false,
		achievements: mythicAchievements,
		isOwnProfile: true,
		targetUsername: mockUser.name,
	},
};

/**
 * View showing another user's progress. Header labels change accordingly.
 */
export const ViewingOthersProfile: Story = {
	args: {
		viewMode: "tree",
		onViewModeChange: () => {},
		isLoading: false,
		isError: false,
		achievements: mythicAchievements,
		isOwnProfile: false,
		targetUsername: "Hercules_Coder",
	},
};

/**
 * List view mode toggle state.
 */
export const ListViewMode: Story = {
	args: {
		viewMode: "list",
		onViewModeChange: () => {},
		isLoading: false,
		isError: false,
		achievements: mythicAchievements,
		isOwnProfile: true,
		targetUsername: mockUser.name,
	},
};

/**
 * Sidebar during initial data fetch.
 */
export const Loading: Story = {
	args: {
		viewMode: "tree",
		onViewModeChange: () => {},
		isLoading: true,
		isError: false,
		achievements: [],
		isOwnProfile: true,
		targetUsername: mockUser.name,
	},
};

/**
 * Sidebar in a failed state.
 */
export const ErrorState: Story = {
	args: {
		viewMode: "tree",
		onViewModeChange: () => {},
		isLoading: false,
		isError: true,
		achievements: [],
		isOwnProfile: true,
		targetUsername: mockUser.name,
	},
};
