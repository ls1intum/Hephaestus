import type { Meta, StoryObj } from "@storybook/react";
import { SidebarProvider } from "@/components/ui/sidebar";
import { NavMentorThreads } from "./NavMentorThreads";

/**
 * Navigation component showing chat thread history in mentor mode.
 * Displays a flat list of previous conversations; the component buckets
 * threads locally by createdAt (Today, Yesterday, Last 7 days, ...).
 */
const meta = {
	component: NavMentorThreads,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		workspaceSlug: "aet",
	},
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

const now = Date.now();
const day = 24 * 60 * 60 * 1000;

/**
 * Default view showing empty state.
 */
export const Default: Story = {
	args: {
		threads: [],
	},
};

/**
 * Shows the complete thread list with various time groups.
 */
export const WithMockData: Story = {
	args: {
		threads: [
			{ id: "1", title: "React Hooks Best Practices", createdAt: new Date() },
			{ id: "2", title: "TypeScript Generic Types", createdAt: new Date() },
			{ id: "3", title: "Database Design Help", createdAt: new Date() },
			{
				id: "4",
				title: "API Architecture Review",
				createdAt: new Date(now - day - 60_000),
			},
			{
				id: "5",
				title: "Frontend Performance Tips",
				createdAt: new Date(now - day - 60_000),
			},
			{
				id: "6",
				title: "Performance Optimization",
				createdAt: new Date(now - 3 * day),
			},
		],
	},
	parameters: {
		docs: {
			description: {
				story: "Displays mock chat threads grouped by time periods with various thread titles.",
			},
		},
	},
};

/**
 * Loading state while fetching threads.
 */
export const Loading: Story = {
	args: {
		threads: [],
		isLoading: true,
	},
};

/**
 * Error state when thread loading fails.
 */
export const ErrorState: Story = {
	args: {
		threads: [],
		error: "Failed to load threads",
	},
};
