import type { TeamInfo } from "@/api/types.gen";
import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { TeamsTable } from "./TeamsTable";
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
		hidden: true,
		repositories: [],
		labels: [],
		members: [],
	},
	{
		id: 5,
		name: "Design Team",
		color: "#8b5cf6",
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
			id: 1,
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
			id: 2,
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
			id: 3,
			name: "Charlie Brown",
			email: "charlie@example.com",
		},
	},
	{
		id: 4,
		login: "diana",
		name: "Diana Prince",
		url: "https://github.com/diana",
		teams: [mockTeams[4]],
		user: {
			id: 4,
			name: "Diana Prince",
			email: "diana@example.com",
		},
	},
	{
		id: 5,
		login: "ethan",
		name: "Ethan Hunt",
		url: "https://github.com/ethan",
		teams: [mockTeams[2], mockTeams[4]],
		user: {
			id: 5,
			name: "Ethan Hunt",
			email: "ethan@example.com",
		},
	},
];

const meta: Meta<typeof TeamsTable> = {
	component: TeamsTable,
	parameters: {
		layout: "fullscreen",
		docs: {
			description: {
				component:
					"A comprehensive data table for managing teams, their properties, and visibility in the workspace.",
			},
		},
	},
	args: {
		teams: mockTeams,
		users: mockUsers,
		onCreateTeam: fn(),
		onDeleteTeam: fn(),
		onToggleTeamVisibility: fn(),
		onUpdateTeam: fn(),
	},
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Loading: Story = {
	args: {
		isLoading: true,
		teams: [],
	},
};

export const EmptyState: Story = {
	args: {
		teams: [],
		users: [],
		isLoading: false,
	},
};

export const SingleTeam: Story = {
	args: {
		teams: [mockTeams[0]],
		users: mockUsers.slice(0, 2),
	},
};

export const TeamsWithoutMembers: Story = {
	args: {
		teams: [
			{
				id: "team-empty-1",
				name: "Empty Team 1",
				color: "#6b7280",
				hidden: false,
			},
			{
				id: "team-empty-2",
				name: "Empty Team 2",
				color: "#dc2626",
				hidden: true,
			},
		],
		users: [],
	},
};

export const ManyTeams: Story = {
	args: {
		teams: Array.from({ length: 25 }, (_, i) => ({
			id: `team-${i + 1}`,
			name: `Team ${i + 1}`,
			color: `hsl(${(i * 137.5) % 360}, 70%, 50%)`,
			hidden: i % 7 === 0,
		})),
		users: mockUsers,
	},
};

export const HiddenTeams: Story = {
	args: {
		teams: mockTeams.map((team) => ({ ...team, hidden: true })),
		users: mockUsers,
	},
};
