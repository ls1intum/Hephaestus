import { SidebarProvider } from "@/components/ui/sidebar";
import type { Meta, StoryObj } from "@storybook/react";
import { NavMentorThreads } from "./NavMentorThreads";

/**
 * Navigation component showing chat thread history in mentor mode.
 * Displays a list of previous conversations with the AI mentor, including timestamps and read status.
 */
const meta = {
	component: NavMentorThreads,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<SidebarProvider className="min-h-0 w-[16rem] border border-border rounded-lg p-2 bg-sidebar">
				<Story />
			</SidebarProvider>
		),
	],
} satisfies Meta<typeof NavMentorThreads>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default view showing chat thread history with various states.
 */
export const Default: Story = {};

/**
 * Shows the complete thread list with active, unread, and read threads.
 */
export const WithMockData: Story = {
	parameters: {
		docs: {
			description: {
				story:
					"Displays mock chat threads showing different states: active thread, unread messages, and various timestamps.",
			},
		},
	},
};
