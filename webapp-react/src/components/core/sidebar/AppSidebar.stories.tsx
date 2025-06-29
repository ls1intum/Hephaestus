import type { ChatThreadGroup } from "@/api/types.gen";
import { SidebarProvider } from "@/components/ui/sidebar";
import type { Meta, StoryObj } from "@storybook/react";
import { AppSidebar } from "./AppSidebar";

/**
 * Main sidebar component for the application, combining all navigation sections and
 * providing access to different areas of the application.
 */
const meta = {
	component: AppSidebar,
	parameters: {
		layout: "fullscreen",
		docs: {
			description: {
				component:
					"Complete application sidebar component that combines all navigation sections and provides access to the entire application.",
			},
		},
	},
	tags: ["autodocs"],
	args: {
		username: "johnDoe",
		isAdmin: false,
	},
	argTypes: {
		username: {
			control: "text",
			description: "Username of the current user",
		},
		isAdmin: {
			control: "boolean",
			description: "Whether the user has administrative privileges",
		},
	},
	decorators: [
		(Story) => (
			<SidebarProvider className="w-full max-w-[16rem]">
				<Story />
			</SidebarProvider>
		),
	],
} satisfies Meta<typeof AppSidebar>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Regular user sidebar without administrative privileges.
 */
export const RegularUser: Story = {
	args: {
		username: "johndoe",
		isAdmin: false,
		hasMentorAccess: false,
		context: "main",
	},
};

/**
 * Admin user sidebar with administrative privileges.
 */
export const AdminUser: Story = {
	args: {
		username: "admin",
		isAdmin: true,
		hasMentorAccess: true,
		context: "main",
	},
};

/**
 * Mentor sidebar context with grouped threads.
 */
export const MentorContext: Story = {
	args: {
		username: "mentor",
		isAdmin: false,
		hasMentorAccess: true,
		context: "mentor",
		mentorThreadGroups: [
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
				],
			},
			{
				groupName: "Yesterday",
				threads: [
					{
						id: "3",
						title: "API Architecture Review",
						createdAt: new Date(Date.now() - 24 * 60 * 60 * 1000),
					},
				],
			},
		] as ChatThreadGroup[],
		mentorThreadsLoading: false,
	},
};

/**
 * Mentor sidebar with loading state.
 */
export const MentorLoading: Story = {
	args: {
		username: "mentor",
		isAdmin: false,
		hasMentorAccess: true,
		context: "mentor",
		mentorThreadsLoading: true,
	},
};
