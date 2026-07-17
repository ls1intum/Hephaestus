import type { Meta, StoryObj } from "@storybook/react";
import { ReactFlowProvider } from "@xyflow/react";
import { expect, within } from "storybook/test";
import { AchievementSidebar } from "@/components/achievements/AchievementSidebar";
import { mockUser, mythicAchievementsUI } from "@/components/achievements/storyMockData";

/**
 * The consolidated achievements sidebar showing stats, progress, recent unlocks,
 * and navigation controls. Used as a persistent right-hand companion on the skill tree page.
 */
const meta = {
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
} satisfies Meta<typeof AchievementSidebar>;

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
		achievements: mythicAchievementsUI,
		isOwnProfile: true,
		targetUsername: mockUser.name,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// The active segment must be visibly raised. Base UI's ToggleGroupItem emits
		// `data-pressed`, which the corrected `data-pressed:bg-background data-pressed:shadow-sm`
		// classes style (the old `data-[state=on]` selector matched nothing).
		const tree = await canvas.findByRole("button", { name: "Tree view" });
		await expect(tree).toHaveAttribute("data-pressed");
		const list = canvas.getByRole("button", { name: "List view" });
		await expect(list).not.toHaveAttribute("data-pressed");
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
		achievements: mythicAchievementsUI,
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
		achievements: mythicAchievementsUI,
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
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// The loading pill uses the vendored <Spinner/> (role="status"), not a bare Loader2 icon.
		await expect(await canvas.findByRole("status", { name: "Loading" })).toBeInTheDocument();
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
