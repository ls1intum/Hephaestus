// Cleaned up duplicate content; single coherent story below
import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import type { TeamInfo } from "@/api/types.gen";
import { AdminTeamsTable } from "./AdminTeamsTable";

const repos = [
	{
		id: 1,
		name: "hephaestus",
		nameWithOwner: "org/hephaestus",
		htmlUrl: "https://github.com/org/hephaestus",
		hiddenFromContributions: false,
	},
	{
		id: 2,
		name: "web-app",
		nameWithOwner: "org/web-app",
		htmlUrl: "https://github.com/org/web-app",
		hiddenFromContributions: false,
	},
	{
		id: 3,
		name: "server",
		nameWithOwner: "org/server",
		htmlUrl: "https://github.com/org/server",
		hiddenFromContributions: false,
	},
];

const labels = [
	{ id: 101, name: "bug", color: "d73a4a", repository: repos[0] },
	{ id: 102, name: "enhancement", color: "a2eeef", repository: repos[1] },
	{ id: 103, name: "help wanted", color: "008672", repository: repos[0] },
	{ id: 104, name: "good first issue", color: "7057ff", repository: repos[2] },
];

/**
 * Teams with hierarchy structure for testing rollup counts.
 * - Frontend (root): 2 members (Sarah, Alex)
 *   - Backend (child): 1 member (Jamie)
 *   - QA (child, hidden): 0 members
 *
 * Rollup counts:
 * - Frontend: 3 (2 direct + 1 from Backend)
 * - Backend: 1 (leaf)
 * - QA: 0 (leaf, hidden)
 */
const teams: TeamInfo[] = [
	{
		id: 1,
		name: "Frontend",
		description: "",
		htmlUrl: "https://github.com/orgs/org/teams/frontend",
		organization: "org",
		privacy: "CLOSED",
		membershipCount: 2,
		repoPermissionCount: 2,
		hidden: false,
		repositories: [repos[0], repos[1]],
		labels: [labels[0], labels[2]],
		members: [
			{
				id: 1,
				login: "sarah",
				name: "Sarah",
				avatarUrl: "",
				htmlUrl: "https://github.com/sarah",
			},
			{
				id: 2,
				login: "alex",
				name: "Alex",
				avatarUrl: "",
				htmlUrl: "https://github.com/alex",
			},
		],
	},
	{
		id: 2,
		name: "Backend",
		description: "",
		htmlUrl: "https://github.com/orgs/org/teams/backend",
		organization: "org",
		privacy: "CLOSED",
		membershipCount: 1,
		repoPermissionCount: 2,
		hidden: false,
		parentId: 1,
		repositories: [repos[2]],
		labels: [labels[3]],
		members: [
			{
				id: 3,
				login: "jamie",
				name: "Jamie",
				avatarUrl: "",
				htmlUrl: "https://github.com/jamie",
			},
		],
	},
	{
		id: 3,
		name: "QA",
		description: "",
		htmlUrl: "https://github.com/orgs/org/teams/qa",
		organization: "org",
		privacy: "CLOSED",
		membershipCount: 0,
		repoPermissionCount: 1,
		hidden: true,
		parentId: 1,
		repositories: [repos[1]],
		labels: [],
		members: [],
	},
];

/**
 * AdminTeamsTable displays teams in a hierarchical tree structure with
 * search, visibility toggles, and member count modes.
 *
 * Use the "Member Count" toggle to switch between:
 * - **Direct**: Count only the team's own members
 * - **Include Subteams**: Count unique members from team + all descendants
 */
const meta = {
	component: AdminTeamsTable,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	argTypes: {
		teams: { control: false },
		isLoading: { control: "boolean" },
		onHideTeam: { action: "hideTeam" },
		onToggleRepositoryVisibility: { action: "toggleRepositoryVisibility" },
		onAddLabelToTeam: { action: "addLabelToTeam" },
		onRemoveLabelFromTeam: { action: "removeLabelFromTeam" },
	},
	args: {
		teams,
		onHideTeam: fn(),
		onToggleRepositoryVisibility: fn(),
		onAddLabelToTeam: fn(),
		onRemoveLabelFromTeam: fn(),
	},
} satisfies Meta<typeof AdminTeamsTable>;

export default meta;
export type Story = StoryObj<typeof meta>;

/**
 * Default state with direct member counting.
 * Toggle the "Include Subteams" button to see rollup counts.
 */
export const Default: Story = {};

/**
 * Loading state with skeleton placeholders.
 */
export const Loading: Story = { args: { isLoading: true, teams: [] } };

/**
 * Deep hierarchy with 3+ levels of nesting.
 * Shows how member counts roll up through multiple levels.
 */
export const DeepHierarchy: Story = {
	args: {
		teams: [
			...teams,
			{
				id: 4,
				name: "API Team",
				description: "",
				htmlUrl: "https://github.com/orgs/org/teams/api",
				organization: "org",
				privacy: "CLOSED",
				membershipCount: 2,
				repoPermissionCount: 1,
				hidden: false,
				parentId: 2, // Child of Backend
				repositories: [],
				labels: [],
				members: [
					{ id: 4, login: "pat", name: "Pat", avatarUrl: "", htmlUrl: "" },
					{ id: 5, login: "quinn", name: "Quinn", avatarUrl: "", htmlUrl: "" },
				],
			},
		],
	},
};

/**
 * Flat structure with no hierarchy (all root teams).
 */
export const FlatStructure: Story = {
	args: {
		teams: teams.map((t) => ({ ...t, parentId: undefined })),
	},
};
