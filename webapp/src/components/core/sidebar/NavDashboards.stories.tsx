import type { Meta, StoryObj } from "@storybook/react";
import { SidebarProvider } from "@/components/ui/sidebar";
import { NavDashboards } from "./NavDashboards";

const meta = {
	component: NavDashboards,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	args: {
		username: "johnDoe",
		workspaceSlug: "aet",
		achievementsEnabled: true,
		leaderboardEnabled: true,
	},
	argTypes: {
		username: {
			control: "text",
			description: "Username of the current user",
		},
		workspaceSlug: {
			control: "text",
			description: "Active workspace slug",
		},
		achievementsEnabled: {
			control: "boolean",
			description: "Whether achievements sidebar item is visible",
		},
		leaderboardEnabled: {
			control: "boolean",
			description: "Whether leaderboard sidebar item is visible",
		},
	},
	decorators: [
		(Story) => (
			<SidebarProvider className="min-h-0 w-[16rem] border border-border rounded-lg p-2 bg-sidebar">
				<Story />
			</SidebarProvider>
		),
	],
} satisfies Meta<typeof NavDashboards>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const DifferentUser: Story = {
	args: {
		username: "janeDoe",
		workspaceSlug: "aet",
	},
};

export const AllFeaturesDisabled: Story = {
	args: {
		achievementsEnabled: false,
		leaderboardEnabled: false,
	},
};
