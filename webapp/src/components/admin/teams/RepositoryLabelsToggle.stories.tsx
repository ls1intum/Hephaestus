import type { Meta, StoryObj } from "@storybook/react";
import type { LabelInfo, RepositoryInfo, TeamInfo } from "@/api/types.gen";
import { RepositoryLabelsToggle } from "./RepositoryLabelsToggle";

/**
 * RepositoryLabelsToggle lists available labels for a repository and lets you toggle them for a team.
 */
const meta = {
	component: RepositoryLabelsToggle,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
} satisfies Meta<typeof RepositoryLabelsToggle>;

export default meta;
type Story = StoryObj<typeof meta>;

const mockRepo: RepositoryInfo = {
	id: 100,
	name: "repo",
	nameWithOwner: "org/repo",
	htmlUrl: "https://github.com/org/repo",
};

const mockTeam: TeamInfo = {
	id: 1,
	name: "Team Alpha",
	labels: [
		{
			id: 1,
			name: "bug",
			color: "d73a4a",
			repository: { id: 100 } as RepositoryInfo,
		} as LabelInfo,
	],
} as TeamInfo;

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
		team: mockTeam,
		repository: mockRepo,
		catalogLabels: catalog,
	},
};

export const WithFilter: Story = {
	args: {
		team: mockTeam,
		repository: mockRepo,
		catalogLabels: catalog,
	},
};
