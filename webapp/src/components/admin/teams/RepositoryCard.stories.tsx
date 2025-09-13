import type { Meta, StoryObj } from "@storybook/react";
import type { LabelInfo, RepositoryInfo, TeamInfo } from "@/api/types.gen";
import { RepositoryCard } from "./RepositoryCard";

/**
 * RepositoryCard displays a repo with active labels and an action to manage them.
 */
const meta = {
	component: RepositoryCard,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
} satisfies Meta<typeof RepositoryCard>;

export default meta;
type Story = StoryObj<typeof meta>;

const repo: RepositoryInfo = {
	id: 100,
	name: "repo",
	nameWithOwner: "org/repo",
	htmlUrl: "https://github.com/org/repo",
	description: "A sample repository",
};

const team: TeamInfo = {
	id: 1,
	name: "Platform",
	labels: [
		{
			id: 1,
			name: "bug",
			color: "d73a4a",
			repository: { id: 100 } as RepositoryInfo,
		} as LabelInfo,
	],
	hidden: false,
	membershipCount: 5,
	repoPermissionCount: 3,
	repositories: [repo],
	members: [],
};

const catalog: LabelInfo[] = [
	{
		id: 1,
		name: "bug",
		color: "d73a4a",
		repository: { id: 100 } as RepositoryInfo,
	} as LabelInfo,
	{
		id: 2,
		name: "feature",
		color: "a2eeef",
		repository: { id: 100 } as RepositoryInfo,
	} as LabelInfo,
	{
		id: 3,
		name: "docs",
		color: "0075ca",
		repository: { id: 100 } as RepositoryInfo,
	} as LabelInfo,
];

export const Default: Story = {
	args: {
		repository: repo,
		team,
		catalogLabels: catalog,
	},
};

export const Filtered: Story = {
	args: {
		repository: repo,
		team,
		catalogLabels: catalog,
	},
};
