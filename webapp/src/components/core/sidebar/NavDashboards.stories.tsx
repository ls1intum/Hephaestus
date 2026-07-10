import type { Meta, StoryObj } from "@storybook/react";
import { SidebarProvider } from "@/components/ui/sidebar";
import { NavDashboards } from "./NavDashboards";

const meta = {
	component: NavDashboards,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"Dashboard navigation sidebar component with links to profile, practice overview, achievements, and teams.",
			},
		},
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "aet",
		achievementsEnabled: true,
		practicesEnabled: true,
		isAdmin: false,
	},
	argTypes: {
		workspaceSlug: {
			control: "text",
			description: "Active workspace slug",
		},
		achievementsEnabled: {
			control: "boolean",
			description: "Whether achievements sidebar item is visible",
		},
		practicesEnabled: {
			control: "boolean",
			description: "Whether the practice overview surface is visible",
		},
		isAdmin: {
			control: "boolean",
			description: "Whether the admin-only Practice Overview item is visible",
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

export const AdminView: Story = {
	args: {
		isAdmin: true,
	},
};

export const AllFeaturesDisabled: Story = {
	args: {
		achievementsEnabled: false,
		practicesEnabled: false,
	},
};
