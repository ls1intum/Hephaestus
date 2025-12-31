import type { Meta, StoryObj } from "@storybook/react";
import type { LabelInfo, RepositoryInfo, TeamInfo, UserInfo } from "@/api/types.gen";
import { TeamTree } from "./TeamTree";

/**
 * TeamTree renders a team with its repositories and nested child teams.
 * Supports member count modes for displaying direct vs rollup counts.
 */
const meta = {
	component: TeamTree,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	argTypes: {
		countMode: {
			control: "radio",
			options: ["direct", "rollup"],
			description: "Member counting mode",
		},
	},
} satisfies Meta<typeof TeamTree>;

export default meta;
type Story = StoryObj<typeof meta>;

// Helper to create members
const createMember = (id: number, login: string): UserInfo => ({
	id,
	login,
	name: login.charAt(0).toUpperCase() + login.slice(1),
	avatarUrl: "",
	htmlUrl: `https://github.com/${login}`,
});

const repoA: RepositoryInfo = {
	id: 100,
	name: "api",
	nameWithOwner: "org/api",
	htmlUrl: "https://github.com/org/api",
	hiddenFromContributions: false,
};
const repoB: RepositoryInfo = {
	id: 101,
	name: "web",
	nameWithOwner: "org/web",
	htmlUrl: "https://github.com/org/web",
	hiddenFromContributions: false,
};
const bug: LabelInfo = {
	id: 1,
	name: "bug",
	color: "d73a4a",
	repository: repoA,
};

const child: TeamInfo = {
	id: 2,
	name: "Frontend",
	parentId: 1,
	hidden: false,
	membershipCount: 2,
	repoPermissionCount: 0,
	repositories: [repoB],
	labels: [],
	members: [createMember(102, "charlie"), createMember(103, "diana")],
};

const parent: TeamInfo = {
	id: 1,
	name: "Platform",
	hidden: false,
	membershipCount: 2,
	repoPermissionCount: 1,
	members: [createMember(100, "alice"), createMember(101, "bob")],
	repositories: [repoA],
	labels: [bug],
};

const childrenMap = new Map<number, TeamInfo[]>([[1, [child]]]);
const displaySet = new Set<number>([1, 2]);

// Member counts: parent has 2 direct, 4 rollup (2 + 2 from child)
const memberCounts = new Map([
	[1, { direct: 2, rollup: 4 }],
	[2, { direct: 2, rollup: 2 }],
]);

export const Default: Story = {
	args: {
		team: parent,
		childrenMap,
		displaySet,
		getCatalogLabels: (repoId: number) => (repoId === 100 ? [bug] : []),
		onToggleVisibility: () => {},
		onToggleRepositoryVisibility: () => {},
	},
};

/**
 * Direct mode: Shows only the team's own member count.
 */
export const DirectCountMode: Story = {
	args: {
		team: parent,
		childrenMap,
		displaySet,
		memberCounts,
		countMode: "direct",
		getCatalogLabels: (repoId: number) => (repoId === 100 ? [bug] : []),
		onToggleVisibility: () => {},
		onToggleRepositoryVisibility: () => {},
	},
};

/**
 * Rollup mode: Shows unique members from team + all subteams.
 * Parent shows "4 members (2 direct)" since it has 2 own members
 * plus 2 unique from the child team.
 */
export const RollupCountMode: Story = {
	args: {
		team: parent,
		childrenMap,
		displaySet,
		memberCounts,
		countMode: "rollup",
		getCatalogLabels: (repoId: number) => (repoId === 100 ? [bug] : []),
		onToggleVisibility: () => {},
		onToggleRepositoryVisibility: () => {},
	},
};

// Deep hierarchy example: 3 levels
const grandchild: TeamInfo = {
	id: 3,
	name: "React Components",
	parentId: 2,
	hidden: false,
	membershipCount: 1,
	repoPermissionCount: 0,
	repositories: [],
	labels: [],
	members: [createMember(104, "eve")],
};

const deepChildrenMap = new Map<number, TeamInfo[]>([
	[1, [child]],
	[2, [grandchild]],
]);
const deepDisplaySet = new Set<number>([1, 2, 3]);
const deepMemberCounts = new Map([
	[1, { direct: 2, rollup: 5 }], // 2 + 2 + 1
	[2, { direct: 2, rollup: 3 }], // 2 + 1
	[3, { direct: 1, rollup: 1 }],
]);

/**
 * Deep hierarchy: 3 levels of nesting (Platform > Frontend > React Components).
 * Shows how rollup counts aggregate through the hierarchy.
 */
export const DeepHierarchy: Story = {
	args: {
		team: parent,
		childrenMap: deepChildrenMap,
		displaySet: deepDisplaySet,
		memberCounts: deepMemberCounts,
		countMode: "rollup",
		getCatalogLabels: () => [],
		onToggleVisibility: () => {},
		onToggleRepositoryVisibility: () => {},
	},
};
