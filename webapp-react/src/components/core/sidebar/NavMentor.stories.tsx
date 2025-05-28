import { SidebarProvider } from "@/components/ui/sidebar";
import type { Meta, StoryObj } from "@storybook/react";
import { NavMentor } from "./NavMentor";

/**
 * Navigation component for AI Mentor features, providing access to the AI
 * mentoring system.
 */
const meta = {
	component: NavMentor,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"Mentor navigation sidebar component with links to AI mentoring features and resources.",
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
} satisfies Meta<typeof NavMentor>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default view of the mentor navigation sidebar.
 */
export const Default: Story = {};
