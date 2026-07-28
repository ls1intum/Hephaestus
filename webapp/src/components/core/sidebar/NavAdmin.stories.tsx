import type { Meta, StoryObj } from "@storybook/react";
import { expect, userEvent, within } from "storybook/test";
import { SidebarProvider } from "@/components/ui/sidebar";
import { NavAdmin } from "./NavAdmin";

const meta = {
	component: NavAdmin,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"Administration navigation sidebar component with links to member management and workspace settings.",
			},
		},
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "aet",
		achievementsEnabled: true,
		practicesEnabled: true,
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
		practicesEnabled: {
			control: "boolean",
			description: "Whether practice management is available",
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

export const PracticesDisabled: Story = {
	args: {
		practicesEnabled: false,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: "Practices" }));
		await expect(canvas.getByRole("link", { name: "Catalog" })).toBeInTheDocument();
		await expect(canvas.getByRole("link", { name: "Practice feedback" })).toBeInTheDocument();
		await expect(canvas.queryByRole("link", { name: "Review settings" })).not.toBeInTheDocument();
	},
};

export const AllFeaturesDisabled: Story = {
	args: {
		achievementsEnabled: false,
		practicesEnabled: false,
	},
};

export const GitLabWorkspace: Story = {
	args: {
		scmProviderType: "GITLAB",
	},
};
