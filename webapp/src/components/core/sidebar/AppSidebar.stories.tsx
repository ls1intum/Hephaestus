import type { Meta, StoryObj } from "@storybook/react";
import { expect, within } from "storybook/test";
import type { ChatThreadSummary } from "@/api/types.gen";
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
	practicesEnabled: true,
	mentorEnabled: true,
	achievementsEnabled: true,
	cohortVisibility: "MENTORS_ONLY",
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
		isAdmin: false,
		isAppAdmin: false,
		hasMentorAccess: false,
		context: "main",
		workspaces: [mockWorkspace],
		activeWorkspace: mockWorkspace,
	},
	argTypes: {
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
		isAdmin: true,
		isAppAdmin: true,
		hasMentorAccess: true,
		context: "main",
		activeWorkspace: mockWorkspace,
	},
};

/**
 * The dedicated instance-admin shell (`context === "admin"`): its own "Back to app" header and
 * section nav, with the workspace switcher suppressed.
 */
export const AdminContext: Story = {
	args: {
		isAppAdmin: true,
		context: "admin",
		activeWorkspace: mockWorkspace,
	},
};

/**
 * Instance-admin shell with ZERO workspaces — the regression guard for the day-one lockout: a
 * freshly bootstrapped APP_ADMIN must still reach /admin (the shell renders, not the NoWorkspace
 * empty state), because the admin context is rendered independent of any active workspace.
 */
export const AdminContextNoWorkspace: Story = {
	args: {
		isAppAdmin: true,
		context: "admin",
		workspaces: [],
		activeWorkspace: undefined,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Instance administration")).toBeInTheDocument();
		await expect(canvas.getByText("Back to app")).toBeInTheDocument();
		await expect(canvas.queryByText(/no workspace/i)).not.toBeInTheDocument();
	},
};

/**
 * Mentor sidebar context with sample threads.
 *
 * NavMentorThreads buckets these locally by createdAt (Today, Yesterday,
 * Last 7 days, etc.) since the server returns a flat list.
 */
export const MentorContext: Story = {
	args: {
		isAdmin: false,
		hasMentorAccess: true,
		context: "mentor",
		mentorThreads: [
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
				title: "API Architecture Review",
				createdAt: new Date(Date.now() - 24 * 60 * 60 * 1000 - 60_000),
			},
		] satisfies ChatThreadSummary[],
		mentorThreadsLoading: false,
	},
};

/**
 * Mentor sidebar with loading state.
 */
export const MentorLoading: Story = {
	args: {
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
		},
		workspaces: [
			{
				...mockWorkspace,
				achievementsEnabled: false,
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

/** User has the MENTOR_ACCESS feature flag but the workspace toggle is off — link hidden. */
export const MentorRoleButFeatureDisabled: Story = {
	args: {
		hasMentorAccess: true,
		activeWorkspace: { ...mockWorkspace, mentorEnabled: false },
		workspaces: [{ ...mockWorkspace, mentorEnabled: false }],
	},
};
