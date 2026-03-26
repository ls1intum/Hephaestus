import type { Meta, StoryObj } from "@storybook/react";
import { SidebarProvider } from "@/components/ui/sidebar";
import { NavAdmin } from "./NavAdmin";

/**
 * Navigation component for administrative features, providing access to user
 * management and workspace settings.
 */
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
		practicesEnabled: true,
	},
	argTypes: {
		workspaceSlug: {
			control: "text",
			description: "Active workspace slug",
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

/**
 * Default view of the administration navigation sidebar with all features enabled.
 */
export const Default: Story = {};

/** Admin navigation with practices feature disabled. */
export const PracticesDisabled: Story = {
	args: {
		practicesEnabled: false,
	},
};
