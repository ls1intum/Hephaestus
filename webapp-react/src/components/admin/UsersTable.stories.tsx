import type { TeamInfo } from "@/api/types.gen";
import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { UsersTable } from "./UsersTable";
import type { ExtendedUserTeams } from "./types";

const mockTeams: TeamInfo[] = [
	{
		id: 1,
		name: "Frontend Team",
		color: "#3b82f6",
		hidden: false,
		repositories: [],
		labels: [],
		members: [],
	},
	{
		id: 2,
		name: "Backend Team",
		color: "#ef4444",
		hidden: false,
		repositories: [],
		labels: [],
		members: [],
	},
	{
		id: 3,
		name: "DevOps Team",
		color: "#10b981",
		hidden: false,
		repositories: [],
		labels: [],
		members: [],
	},
	{
		id: 4,
		name: "QA Team",
		color: "#f59e0b",
		hidden: false,
		repositories: [],
		labels: [],
		members: [],
	},
];

const mockUsers: ExtendedUserTeams[] = [
	{
		id: 1,
		login: "alice",
		name: "Alice Johnson",
		url: "https://github.com/alice",
		teams: [mockTeams[0], mockTeams[2]],
		user: {
			id: "user-1",
			name: "Alice Johnson",
			email: "alice@example.com",
		},
	},
	{
		id: 2,
		login: "bob",
		name: "Bob Smith",
		url: "https://github.com/bob",
		teams: [mockTeams[1]],
		user: {
			id: "user-2",
			name: "Bob Smith",
			email: "bob@example.com",
		},
	},
	{
		id: 3,
		login: "charlie",
		name: "Charlie Brown",
		url: "https://github.com/charlie",
		teams: [mockTeams[0], mockTeams[1], mockTeams[3]],
		user: {
			id: "user-3",
			name: "Charlie Brown",
			email: "charlie@example.com",
		},
	},
	{
		id: 4,
		login: "diana",
		name: "Diana Prince",
		url: "https://github.com/diana",
		teams: [],
		user: {
			id: "user-4",
			name: "Diana Prince",
			email: "diana@example.com",
		},
	},
	{
		id: 5,
		login: "ethan",
		name: "Ethan Hunt",
		url: "https://github.com/ethan",
		teams: [mockTeams[2], mockTeams[3]],
		user: {
			id: "user-5",
			name: "Ethan Hunt",
			email: "ethan@example.com",
		},
	},
];

const meta: Meta<typeof UsersTable> = {
	component: UsersTable,
	parameters: {
		layout: "fullscreen",
		docs: {
			description: {
				component:
					"A comprehensive data table for managing users and their team assignments in the workspace.",
			},
		},
	},
	args: {
		users: mockUsers,
		teams: mockTeams,
		onAddTeamToUser: fn(),
		onRemoveUserFromTeam: fn(),
		onBulkAddTeam: fn(),
		onBulkRemoveTeam: fn(),
	},
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Loading: Story = {
	args: {
		isLoading: true,
		users: [],
	},
};

export const EmptyState: Story = {
	args: {
		users: [],
		isLoading: false,
	},
};

export const SingleUser: Story = {
	args: {
		users: [mockUsers[0]],
	},
};

export const UsersWithoutTeams: Story = {
	args: {
		users: [
			{
				id: 6,
				login: "john",
				name: "John Doe",
				url: "https://github.com/john",
				teams: [],
				user: {
					id: "user-6",
					name: "John Doe",
					email: "john@example.com",
				},
			},
			{
				id: 7,
				login: "jane",
				name: "Jane Smith",
				url: "https://github.com/jane",
				teams: [],
				user: {
					id: "user-7",
					name: "Jane Smith",
					email: "jane@example.com",
				},
			},
		],
	},
};

export const ManyUsers: Story = {
	args: {
		users: Array.from({ length: 50 }, (_, i) => ({
			id: i + 10,
			login: `user${i + 1}`,
			name: `User ${i + 1}`,
			url: `https://github.com/user${i + 1}`,
			teams: mockTeams.filter(() => Math.random() > 0.5),
			user: {
				id: `user-${i + 10}`,
				name: `User ${i + 1}`,
				email: `user${i + 1}@example.com`,
			},
		})),
	},
};
