import type { Meta, StoryObj } from "@storybook/react";
import type { LabelInfo, RepositoryInfo, TeamInfo } from "@/api/types.gen";
import { TeamTree } from "./TeamTree";

/**
 * TeamTree renders a team with its repositories and nested child teams.
 */
const meta = {
	component: TeamTree,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
} satisfies Meta<typeof TeamTree>;

export default meta;
type Story = StoryObj<typeof meta>;

const repoA = {
	id: 100,
	nameWithOwner: "org/api",
	htmlUrl: "https://github.com/org/api",
} as RepositoryInfo;
const repoB = {
	id: 101,
	nameWithOwner: "org/web",
	htmlUrl: "https://github.com/org/web",
} as RepositoryInfo;
const bug = {
	id: 1,
	name: "bug",
	color: "d73a4a",
	repository: { id: 100 } as RepositoryInfo,
} as LabelInfo;

const child = {
	id: 2,
	name: "Frontend",
	repositories: [repoB],
	labels: [],
} as unknown as TeamInfo;

const parent = {
	id: 1,
	name: "Platform",
	members: [{ id: 10, login: "alice" }],
	repositories: [repoA],
	labels: [bug],
} as unknown as TeamInfo;

const childrenMap = new Map<number, TeamInfo[]>([[1, [child]]]);
const displaySet = new Set<number>([1, 2]);

export const Default: Story = {
	args: {
		team: parent,
		childrenMap,
		displaySet,
		getCatalogLabels: (repoId: number) => (repoId === 100 ? [bug] : []),
		onToggleVisibility: () => {},
	},
};
