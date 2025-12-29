import type { Meta, StoryObj } from "@storybook/react";
import { SidebarProvider } from "@/components/ui/sidebar";
import { NavMentor } from "./NavMentor";

/**
 * Navigation component for AI Mentor features, providing access to the AI
 * mentoring system. Shows a chevron arrow on hover to indicate expandable behavior.
 */
const meta = {
	component: NavMentor,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"Mentor navigation sidebar component with links to AI mentoring features. Displays a chevron on hover to indicate it will transform the sidebar into mentor mode.",
			},
		},
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "aet",
	},
	argTypes: {
		workspaceSlug: {
			control: "text",
			description: "Active workspace slug",
		},
	},
	decorators: [
		(Story) => (
			<SidebarProvider className="min-h-0 w-[16rem] border border-border rounded-lg p-2 bg-sidebar">
				<Story />
			</SidebarProvider>
		),
	],
} satisfies Meta<typeof NavMentor>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default view of the mentor navigation with hover chevron indicator.
 */
export const Default: Story = {};

/**
 * Shows the mentor navigation in hover state, revealing the chevron arrow.
 */
export const WithHoverState: Story = {
	parameters: {
		docs: {
			description: {
				story:
					"Hover over the mentor item to see the chevron arrow that indicates clicking will transform the sidebar into mentor mode.",
			},
		},
	},
};
