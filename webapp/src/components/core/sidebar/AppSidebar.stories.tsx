import type { Meta, StoryObj } from "@storybook/react";
import type { ChatThreadGroup } from "@/api/types.gen";
import { SidebarProvider } from "@/components/ui/sidebar";
import { AppSidebar } from "./AppSidebar";

const mockWorkspace = {
	displayName: "AET",
	accountLogin: "aet-org",
	workspaceSlug: "aet",
	id: 1,
	status: "ACTIVE",
	providerType: "GITHUB",
	createdAt: new Date("2025-01-15T00:00:00Z"),
	achievementsEnabled: true,
	leaderboardEnabled: true,
	practicesEnabled: true,
	progressionEnabled: false,
} as const;

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
		hasMentorAccess: false,
		context: "main",
		workspaces: [mockWorkspace],
		activeWorkspace: mockWorkspace,
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
		workspacesLoading: {
			control: "boolean",
			description: "Shows loading skeletons while workspaces are being fetched",
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
		activeWorkspace: mockWorkspace,
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
		activeWorkspace: mockWorkspace,
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
						createdAt: new Date("2025-01-15T00:00:00Z"),
					},
					{
						id: "2",
						title: "TypeScript Generic Types",
						createdAt: new Date("2025-01-15T00:00:00Z"),
					},
				],
			},
			{
				groupName: "Yesterday",
				threads: [
					{
						id: "3",
						title: "API Architecture Review",
						createdAt: new Date("2025-01-14T00:00:00Z"),
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

/**
 * Sidebar when all features are disabled — only Profile and Teams are visible.
 */
export const AllFeaturesDisabled: Story = {
	args: {
		activeWorkspace: {
			...mockWorkspace,
			achievementsEnabled: false,
			leaderboardEnabled: false,
			practicesEnabled: false,
		},
		workspaces: [
			{
				...mockWorkspace,
				achievementsEnabled: false,
				leaderboardEnabled: false,
				practicesEnabled: false,
			},
		],
	},
};

/**
 * Sidebar state when the user is not in any workspace.
 */
export const NoWorkspace: Story = {
	args: {
		workspaces: [],
		activeWorkspace: undefined,
	},
};

/**
 * Sidebar while workspaces are being fetched (skeletons only).
 */
export const LoadingWorkspaces: Story = {
	args: {
		workspaces: [],
		activeWorkspace: undefined,
		workspacesLoading: true,
	},
};
