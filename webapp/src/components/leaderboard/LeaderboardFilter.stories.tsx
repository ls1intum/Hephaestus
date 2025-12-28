import type { Meta, StoryObj } from "@storybook/react-vite";
import { LeaderboardFilter } from "./LeaderboardFilter";

const meta: Meta<typeof LeaderboardFilter> = {
	component: LeaderboardFilter,
	tags: ["autodocs"],
};

export default meta;
type Story = StoryObj<typeof LeaderboardFilter>;

export const Default: Story = {
	args: {
		teamOptions: [
			{ value: "Frontend", label: "Frontend" },
			{ value: "Backend", label: "Backend" },
			{ value: "DevOps", label: "DevOps" },
			{ value: "QA", label: "QA" },
			{ value: "Design", label: "Design" },
		],
		selectedTeam: "all",
		selectedSort: "SCORE",
	},
};

export const WithSelectedFilters: Story = {
	args: {
		teamOptions: [
			{ value: "Frontend", label: "Frontend" },
			{ value: "Backend", label: "Backend" },
			{ value: "DevOps", label: "DevOps" },
			{ value: "QA", label: "QA" },
			{ value: "Design", label: "Design" },
		],
		selectedTeam: "Frontend",
		selectedSort: "LEAGUE_POINTS",
	},
};
