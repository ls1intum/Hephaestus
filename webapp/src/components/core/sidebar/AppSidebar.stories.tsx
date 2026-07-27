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
	leaderboardEnabled: true,
	progressionEnabled: false,
	leaguesEnabled: false,
} as const;

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
		isAppAdmin: false,
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

/** A member never sees the workspace-admin nav — hidden, not shown disabled. */
export const RegularUser: Story = {
	args: {
		username: "johndoe",
		isAdmin: false,
		hasMentorAccess: false,
		context: "main",
		activeWorkspace: mockWorkspace,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.queryByText("Administration")).not.toBeInTheDocument();
	},
};

export const WorkspaceAdminUser: Story = {
	args: {
		username: "admin",
		isAdmin: true,
		isAppAdmin: false,
		hasMentorAccess: true,
		context: "main",
		activeWorkspace: mockWorkspace,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Administration")).toBeInTheDocument();
		await expect(canvas.queryByText("Instance admin")).not.toBeInTheDocument();
	},
};

export const AdminUser: Story = {
	args: {
		username: "admin",
		isAdmin: true,
		isAppAdmin: true,
		hasMentorAccess: true,
		context: "main",
		activeWorkspace: mockWorkspace,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Administration")).toBeInTheDocument();
	},
};

/** The dedicated instance-admin shell: its own header and section nav, no workspace switcher. */
export const AdminContext: Story = {
	args: {
		username: "admin",
		isAppAdmin: true,
		context: "admin",
		activeWorkspace: mockWorkspace,
	},
};

/** The day-one lockout guard: a freshly bootstrapped APP_ADMIN reaches /admin with no workspace. */
export const AdminContextNoWorkspace: Story = {
	args: {
		username: "admin",
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

/** The server returns a flat list; `NavMentorThreads` buckets it locally by `createdAt`. */
export const MentorContext: Story = {
	args: {
		username: "mentor",
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

export const MentorLoading: Story = {
	args: {
		username: "mentor",
		isAdmin: false,
		hasMentorAccess: true,
		context: "mentor",
		mentorThreadsLoading: true,
	},
};

export const AllFeaturesDisabled: Story = {
	args: {
		activeWorkspace: {
			...mockWorkspace,
			achievementsEnabled: false,
			leaderboardEnabled: false,
		},
		workspaces: [
			{
				...mockWorkspace,
				achievementsEnabled: false,
				leaderboardEnabled: false,
			},
		],
	},
};

export const NoWorkspace: Story = {
	args: {
		workspaces: [],
		activeWorkspace: undefined,
	},
};

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
