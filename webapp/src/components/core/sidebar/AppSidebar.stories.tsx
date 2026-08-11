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
	},
	tags: ["autodocs"],
	args: {
		username: "johnDoe",
		isAdmin: false,
		isAppAdmin: false,
		hasMentorAccess: false,
		integrationKinds: ["GITHUB", "SLACK", "OUTLINE"],
		context: "main",
		workspaces: [mockWorkspace],
		activeWorkspace: mockWorkspace,
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
		canvas.getByText("Administration");
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
		canvas.getByText("Administration");
	},
};

export const AdminContext: Story = {
	args: {
		username: "admin",
		isAppAdmin: true,
		context: "admin",
		activeWorkspace: mockWorkspace,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText("Practice catalog");
		const content = canvasElement.querySelector<HTMLElement>('[data-slot="sidebar-content"]');
		if (!content) throw new Error("sidebar content not found");
		await expect(content.scrollWidth).toBeLessThanOrEqual(content.clientWidth);
	},
};

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
		canvas.getByText("Instance administration");
		canvas.getByText("Back to app");
		await expect(canvas.queryByText(/no workspace/i)).not.toBeInTheDocument();
	},
};

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

export const MentorRoleButFeatureDisabled: Story = {
	args: {
		hasMentorAccess: true,
		activeWorkspace: { ...mockWorkspace, mentorEnabled: false },
		workspaces: [{ ...mockWorkspace, mentorEnabled: false }],
	},
};
