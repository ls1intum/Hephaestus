import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn } from "storybook/test";

import type { TeamInfo } from "@/api/types.gen";
import type { ExtendedUserTeams } from "@/components/admin/members/user-teams";
import { withStandardPage } from "@/stories/decorators";
import { expectNoPageOverflow, expectTablesScrollInPlace } from "@/stories/reflow";

import { WorkspaceMembersPage } from "./WorkspaceMembersPage";

const meta = {
	component: WorkspaceMembersPage,
	parameters: {
		layout: "fullscreen",
	},
	decorators: [withStandardPage],
	tags: ["autodocs"],
	args: {
		view: { q: "", team: "all", sort: "name", desc: false, page: 0, size: 10 },
		onViewChange: fn(),
		renderPageLink: (page, props) => <a {...props} href={`?page=${page}`} />,
	},
} satisfies Meta<typeof WorkspaceMembersPage>;

export default meta;
type Story = StoryObj<typeof meta>;

const frontendTeam: TeamInfo = {
	id: 1,
	name: "Frontend",
	membershipCount: 0,
	repoPermissionCount: 0,
	slug: "team-1",
	repositories: [],
	labels: [],
	members: [],
	hidden: false,
};

const backendTeam: TeamInfo = {
	id: 2,
	name: "Backend",
	membershipCount: 0,
	repoPermissionCount: 0,
	slug: "team-2",
	repositories: [],
	labels: [],
	members: [],
	hidden: false,
};

const devopsTeam: TeamInfo = {
	id: 3,
	name: "DevOps",
	membershipCount: 0,
	repoPermissionCount: 0,
	slug: "team-3",
	repositories: [],
	labels: [],
	members: [],
	hidden: false,
};

const mockTeams: TeamInfo[] = [frontendTeam, backendTeam, devopsTeam];

const mockUsers: ExtendedUserTeams[] = [
	{
		id: 1,
		login: "alice.smith",
		name: "Alice Smith",
		hidden: false,
		url: "https://github.com/alice.smith",
		teams: [frontendTeam],
		user: {
			id: 1,
			name: "Alice Smith",
			login: "alice.smith",
			email: "alice.smith@example.com",
			role: "user",
		},
	},
	{
		id: 2,
		login: "bob.johnson",
		name: "Bob Johnson",
		hidden: false,
		url: "https://github.com/bob.johnson",
		teams: [backendTeam, devopsTeam],
		user: {
			id: 2,
			name: "Bob Johnson",
			login: "bob.johnson",
			email: "bob.johnson@example.com",
			role: "admin",
		},
	},
	{
		id: 3,
		login: "carol.williams",
		name: "Carol Williams",
		hidden: false,
		url: "https://github.com/carol.williams",
		teams: [frontendTeam, backendTeam],
		user: {
			id: 3,
			name: "Carol Williams",
			login: "carol.williams",
			email: "carol.williams@example.com",
			role: "user",
		},
	},
	{
		id: 4,
		login: "david.brown",
		name: "David Brown",
		hidden: false,
		url: "https://github.com/david.brown",
		teams: [],
		user: {
			id: 4,
			name: "David Brown",
			login: "david.brown",
			email: "david.brown@example.com",
			role: "user",
		},
	},
];

export const Default: Story = {
	args: {
		users: mockUsers,
		teams: mockTeams,
		isLoading: false,
	},
};

export const Loading: Story = {
	args: {
		users: [],
		teams: [],
		isLoading: true,
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("table", { name: "Workspace members" })).toHaveAttribute(
			"aria-busy",
			"true",
		);
		await expect(canvas.queryByText("No members found")).not.toBeInTheDocument();
	},
};

export const EmptyUsers: Story = {
	args: {
		users: [],
		teams: mockTeams,
		isLoading: false,
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("This workspace has no members yet.")).toBeVisible();
	},
};

/** The members could not be read: the page says so and shows no rows. */
export const MembersUnavailable: Story = {
	args: {
		users: [],
		teams: mockTeams,
		isLoading: false,
		error: { status: 503 },
		onRetry: fn(),
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("We could not load members")).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Retry" })).toBeVisible();
		await expect(canvas.queryByRole("table")).not.toBeInTheDocument();
	},
};

/** Only the team filter failed: the members still list, and the teams are retried on their own. */
export const TeamsUnavailable: Story = {
	args: {
		users: mockUsers,
		teams: [],
		isLoading: false,
		teamsError: { status: 503 },
		onRetryTeams: fn(),
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("We could not load teams")).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Retry" })).toBeVisible();
		await expect(canvas.getByText("Alice Smith")).toBeVisible();
		await expect(canvas.getByText("David Brown")).toBeVisible();
	},
};

/** The teams were refused: nothing to retry, and the members still list. */
export const TeamsRefused: Story = {
	args: {
		users: mockUsers,
		teams: [],
		isLoading: false,
		teamsError: { status: 403 },
		onRetryTeams: fn(),
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("You do not have access to teams")).toBeVisible();
		await expect(canvas.queryByRole("button", { name: "Retry" })).not.toBeInTheDocument();
		await expect(canvas.getByText("Alice Smith")).toBeVisible();
	},
};

export const NoTeams: Story = {
	args: {
		users: mockUsers.map((user) => ({
			...user,
			teams: [],
		})),
		teams: [],
		isLoading: false,
	},
};

export const MobileReflow: Story = {
	args: {
		users: mockUsers,
		teams: mockTeams,
		isLoading: false,
	},
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320] },
	},
	play: async () => {
		await expectNoPageOverflow();
		await expectTablesScrollInPlace();
	},
};
