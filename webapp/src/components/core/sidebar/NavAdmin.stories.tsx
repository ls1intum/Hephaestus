import type { Meta, StoryObj } from "@storybook/react";
import { SidebarProvider } from "@/components/ui/sidebar";
import { NavAdmin } from "./NavAdmin";

const meta = {
	component: NavAdmin,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "aet",
		achievementsEnabled: true,
	},
	argTypes: {
		workspaceSlug: {
			control: "text",
			description: "Active workspace slug",
		},
		achievementsEnabled: {
			control: "boolean",
			description: "Whether achievement management is available",
		},
	},
	decorators: [
		(Story) => (
			<SidebarProvider className="min-h-0 w-[16rem] border border-border rounded-lg p-2 bg-sidebar">
				<Story />
			</SidebarProvider>
		),
	],
} satisfies Meta<typeof NavAdmin>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const AchievementsDisabled: Story = {
	args: {
		achievementsEnabled: false,
	},
};

export const GitLabWorkspace: Story = {
	args: {
		scmProviderType: "GITLAB",
	},
};
