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
 * Default view showing empty state.
 */
export const Default: Story = {
	args: {
		threadGroups: [],
	},
};

/**
 * Shows the complete thread list with various time groups.
 */
export const WithMockData: Story = {
	args: {
		threadGroups: [
			{
				groupName: "Today",
				threads: [
					{
						id: "1",
						title: "React Hooks Best Practices",
						createdAt: new Date(),
					},
					{
						id: "2",
						title: "TypeScript Generic Types",
						createdAt: new Date(),
					},
					{
						id: "3",
						title: "Database Design Help",
						createdAt: new Date(),
					},
				],
			},
			{
				groupName: "Yesterday",
				threads: [
					{
						id: "4",
						title: "API Architecture Review",
						createdAt: new Date(Date.now() - 24 * 60 * 60 * 1000),
					},
					{
						id: "5",
						title: "Frontend Performance Tips",
						createdAt: new Date(Date.now() - 24 * 60 * 60 * 1000),
					},
				],
			},
			{
				groupName: "Last 7 Days",
				threads: [
					{
						id: "6",
						title: "Performance Optimization",
						createdAt: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000),
					},
				],
			},
		],
	},
	parameters: {
		docs: {
			description: {
				story:
					"Displays mock chat threads grouped by time periods with various thread titles.",
			},
		},
	},
};

/**
 * Loading state while fetching threads.
 */
export const Loading: Story = {
	args: {
		threadGroups: [],
		isLoading: true,
	},
};

/**
 * Error state when thread loading fails.
 */
export const ErrorState: Story = {
	args: {
		threadGroups: [],
		error: "Failed to load threads",
	},
};
