import type { Meta, StoryObj } from "@storybook/react";
import type { TeamInfo } from "@/api/types.gen";
import { TeamCard } from "./TeamCard";

/**
 * TeamCard shows a team's basic info and a slot for repositories/children.
 * Use it to present a team header with visibility controls.
 *
 * Supports member count modes:
 * - **direct**: Shows only direct team members
 * - **rollup**: Shows unique members from team + all subteams
 */
const meta = {
	component: TeamCard,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	argTypes: {
		countMode: {
			control: "radio",
			options: ["direct", "rollup"],
			description: "Member counting mode",
		},
	},
	args: {
		team: {
			id: 1,
			name: "Platform Team",
			hidden: false,
			membershipCount: 1,
			repoPermissionCount: 0,
			members: [
				{
					id: 10,
					login: "alice",
					name: "Alice",
					avatarUrl: "",
					htmlUrl: "https://github.com/alice",
				},
			],
			repositories: [
				{
					id: 101,
					name: "repo",
					nameWithOwner: "org/repo",
					htmlUrl: "https://github.com/org/repo",
					hiddenFromContributions: false,
				},
			],
			labels: [],
		} satisfies TeamInfo,
		memberCount: 1,
		countMode: "direct",
	},
} satisfies Meta<typeof TeamCard>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	args: {
		onToggleVisibility: () => {},
		getCatalogLabels: () => [],
		children: <div className="text-sm text-muted-foreground">Repositories/children slot</div>,
	},
};

export const Hidden: Story = {
	args: {
		team: {
			id: 1,
			name: "Platform Team",
			hidden: true,
			membershipCount: 5,
			repoPermissionCount: 0,
			members: [],
			repositories: [],
			labels: [],
		} satisfies TeamInfo,
		memberCount: 5,
		onToggleVisibility: () => {},
		getCatalogLabels: () => [],
	},
};

/**
 * Rollup mode showing combined member count from team + subteams.
 * When rollup count differs from direct count, shows "(X direct)" annotation.
 */
export const RollupMode: Story = {
	args: {
		team: {
			id: 1,
			name: "Engineering",
			hidden: false,
			membershipCount: 3,
			repoPermissionCount: 0,
			members: [
				{ id: 10, login: "alice", name: "Alice", avatarUrl: "", htmlUrl: "" },
				{ id: 11, login: "bob", name: "Bob", avatarUrl: "", htmlUrl: "" },
				{
					id: 12,
					login: "charlie",
					name: "Charlie",
					avatarUrl: "",
					htmlUrl: "",
				},
			],
			repositories: [],
			labels: [],
		} satisfies TeamInfo,
		memberCount: 3,
		rollupMemberCount: 8, // Team has 3 direct + 5 from subteams (with some overlap)
		countMode: "rollup",
		onToggleVisibility: () => {},
		getCatalogLabels: () => [],
		children: <div className="text-sm text-muted-foreground">Subteams would appear here</div>,
	},
};

/**
 * Rollup mode where all members are direct (no subteam members).
 */
export const RollupModeNoSubteamMembers: Story = {
	args: {
		team: {
			id: 1,
			name: "Leaf Team",
			hidden: false,
			membershipCount: 2,
			repoPermissionCount: 0,
			members: [
				{ id: 10, login: "alice", name: "Alice", avatarUrl: "", htmlUrl: "" },
				{ id: 11, login: "bob", name: "Bob", avatarUrl: "", htmlUrl: "" },
			],
			repositories: [],
			labels: [],
		} satisfies TeamInfo,
		memberCount: 2,
		rollupMemberCount: 2, // Same as direct - no subteam members
		countMode: "rollup",
		onToggleVisibility: () => {},
		getCatalogLabels: () => [],
	},
};
