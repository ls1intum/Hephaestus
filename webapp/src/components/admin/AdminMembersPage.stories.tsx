import type { Meta, StoryObj } from "@storybook/react";

import type { TeamInfo } from "@/api/types.gen";
import type { ExtendedUserTeams } from "@/components/admin/types";
import { AdminMembersPage } from "./AdminMembersPage";

/**
 * Admin page for managing members and their team assignments.
 * Provides comprehensive user management with bulk operations and automatic team assignment.
 */
const meta = {
	component: AdminMembersPage,
	parameters: {
		layout: "fullscreen",
	},
	tags: ["autodocs"],
	argTypes: {
		users: {
			description: "List of users with their team assignments",
		},
		teams: {
			description: "Available teams for assignment",
		},
		isLoading: {
			control: "boolean",
			description: "Loading state for data fetching",
		},
		// Auto-assign removed; no extra loading state beyond isLoading
	},
} satisfies Meta<typeof AdminMembersPage>;

export default meta;
type Story = StoryObj<typeof meta>;

// Mock data
const mockTeams: TeamInfo[] = [
	{
		id: 1,
		name: "Frontend",
		membershipCount: 0,
		repoPermissionCount: 0,
		repositories: [],
		labels: [],
		members: [],
		hidden: false,
	},
	{
		id: 2,
		name: "Backend",
		membershipCount: 0,
		repoPermissionCount: 0,
		repositories: [],
		labels: [],
		members: [],
		hidden: false,
	},
	{
		id: 3,
		name: "DevOps",
		membershipCount: 0,
		repoPermissionCount: 0,
		repositories: [],
		labels: [],
		members: [],
		hidden: false,
	},
];

const mockUsers: ExtendedUserTeams[] = [
	{
		id: 1,
		login: "alice.smith",
		name: "Alice Smith",
		url: "https://github.com/alice.smith",
		teams: [mockTeams[0]],
		user: {
			id: 1,
			name: "Alice Smith",
			email: "alice.smith@example.com",
			role: "user",
		},
	},
	{
		id: 2,
		login: "bob.johnson",
		name: "Bob Johnson",
		url: "https://github.com/bob.johnson",
		teams: [mockTeams[1], mockTeams[2]],
		user: {
			id: 2,
			name: "Bob Johnson",
			email: "bob.johnson@example.com",
			role: "admin",
		},
	},
	{
		id: 3,
		login: "carol.williams",
		name: "Carol Williams",
		url: "https://github.com/carol.williams",
		teams: [mockTeams[0], mockTeams[1]],
		user: {
			id: 3,
			name: "Carol Williams",
			email: "carol.williams@example.com",
			role: "user",
		},
	},
	{
		id: 4,
		login: "david.brown",
		name: "David Brown",
		url: "https://github.com/david.brown",
		teams: [],
		user: {
			id: 4,
			name: "David Brown",
			email: "david.brown@example.com",
			role: "user",
		},
	},
];

/**
 * Default state showing the admin members page with users and teams loaded.
 */
export const Default: Story = {
	args: {
		users: mockUsers,
		teams: mockTeams,
		isLoading: false,
	},
};

/**
 * Loading state while data is being fetched.
 */
export const Loading: Story = {
	args: {
		users: [],
		teams: [],
		isLoading: true,
	},
};

/**
 * State during automatic team assignment operation.
 */
// Removed AssigningTeams story; auto-assign is no longer available

/**
 * Empty state with no users to manage.
 */
export const EmptyUsers: Story = {
	args: {
		users: [],
		teams: mockTeams,
		isLoading: false,
	},
};

/**
 * State with no teams available for assignment.
 */
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
