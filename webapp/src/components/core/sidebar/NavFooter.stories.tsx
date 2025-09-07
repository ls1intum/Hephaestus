import type { Meta, StoryObj } from "@storybook/react";
import { SidebarProvider } from "@/components/ui/sidebar";
import { NavFooter } from "./NavFooter";

/**
 * Navigation component for the sidebar footer, providing access to settings,
 * issue reporting, and other utility functions.
 */
const meta = {
	component: NavFooter,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"Footer navigation sidebar component with links to settings, issue reporting, and other utility functions.",
			},
		},
	},
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<SidebarProvider className="min-h-0 w-[16rem] border border-border rounded-lg p-2 bg-sidebar">
				<Story />
			</SidebarProvider>
		),
	],
} satisfies Meta<typeof NavFooter>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default view of the footer navigation sidebar.
 */
export const Default: Story = {};
