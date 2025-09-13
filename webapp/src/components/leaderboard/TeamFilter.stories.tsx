import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { TeamFilter, type TeamFilterOption } from "./TeamFilter";

/**
 * Team filter component allowing users to filter leaderboard results by team.
 * Includes an "All Teams" option and a list of available teams.
 */
const meta = {
	component: TeamFilter,
	tags: ["autodocs"],
	parameters: {
		layout: "centered",
	},
	argTypes: {
		options: {
			description: "Team options with value and visible path label",
			control: "object",
		},
		selectedTeam: {
			description: 'Currently selected team filter (defaults to "all")',
			control: "text",
		},
		onTeamChange: {
			description: "Callback when team selection changes",
			action: "team changed",
		},
	},
	args: {
		onTeamChange: fn(),
	},
} satisfies Meta<typeof TeamFilter>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default view showing all teams option selected with multiple teams available.
 */
export const Default: Story = {
	args: {
		options: [
			{ value: "Frontend", label: "Frontend" },
			{ value: "Backend", label: "Backend" },
			{ value: "DevOps", label: "DevOps" },
			{ value: "QA", label: "QA" },
			{ value: "Design", label: "Design" },
		] satisfies TeamFilterOption[],
		selectedTeam: "all",
	},
};

/**
 * Single team selected to filter results.
 */
export const TeamSelected: Story = {
	args: {
		options: [
			{ value: "Frontend", label: "Frontend" },
			{ value: "Backend", label: "Backend" },
			{ value: "DevOps", label: "DevOps" },
			{ value: "QA", label: "QA" },
			{ value: "Design", label: "Design" },
		] satisfies TeamFilterOption[],
		selectedTeam: "Frontend",
	},
};

/**
 * Empty state with no teams available to filter by.
 */
export const NoTeams: Story = {
	args: {
		options: [] as TeamFilterOption[],
		selectedTeam: "all",
	},
};

export const WithSelectedTeam: Story = {
	args: {
		options: [
			{ value: "Frontend", label: "Frontend" },
			{ value: "Backend", label: "Backend" },
			{ value: "DevOps", label: "DevOps" },
			{ value: "QA", label: "QA" },
			{ value: "Design", label: "Design" },
		] satisfies TeamFilterOption[],
		selectedTeam: "Frontend",
	},
};

export const EmptyTeamList: Story = {
	args: {
		options: [] as TeamFilterOption[],
		selectedTeam: "all",
	},
};
