import type { Meta, StoryObj } from "@storybook/react";
import { ReactFlowProvider } from "@xyflow/react";
import type { Achievement } from "@/api/types.gen";
import { AchievementHeader } from "./AchievementHeader.tsx";
import { AchievementNode } from "./AchievementNode.tsx";
import type { AchievementNodeData } from "./data";
import { SkillTree } from "./SkillTree.tsx";
import { StatsPanel } from "./stats-panel";

// Mock achievements data for stories
const mockAchievements: Achievement[] = [
	{
		id: "commit-1",
		name: "First Commit",
		description: "Make your first commit to the repository",
		category: "commits",
		rarity: "common",
		parentId: undefined,
		status: "unlocked",
		icon: "GitCommit",
		progress: 1,
		maxProgress: 1,
		unlockedAt: new Date("2024-01-15"),
	},
	{
		id: "commit-5",
		name: "Getting Started",
		description: "Push 5 commits to the repository",
		category: "commits",
		rarity: "uncommon",
		parentId: "commit-1",
		status: "unlocked",
		icon: "GitCommit",
		progress: 5,
		maxProgress: 5,
		unlockedAt: new Date("2024-01-18"),
	},
	{
		id: "commit-10",
		name: "Commit Apprentice",
		description: "Push 10 commits to the repository",
		category: "commits",
		rarity: "rare",
		parentId: "commit-5",
		status: "available",
		icon: "GitCommit",
		progress: 8,
		maxProgress: 10,
		unlockedAt: undefined,
	},
	{
		id: "pr-1",
		name: "First Pull",
		description: "Open your first pull request",
		category: "pull_requests",
		rarity: "common",
		parentId: undefined,
		status: "unlocked",
		icon: "GitPullRequest",
		progress: 1,
		maxProgress: 1,
		unlockedAt: new Date("2024-01-16"),
	},
	{
		id: "review-1",
		name: "First Review",
		description: "Submit your first code review",
		category: "communication",
		rarity: "common",
		parentId: undefined,
		status: "unlocked",
		icon: "Eye",
		progress: 1,
		maxProgress: 1,
		unlockedAt: new Date("2024-01-18"),
	},
	{
		id: "issue-1",
		name: "First Issue",
		description: "Create your first issue",
		category: "issues",
		rarity: "common",
		parentId: undefined,
		status: "locked",
		icon: "CircleDot",
		progress: 0,
		maxProgress: 1,
		unlockedAt: undefined,
	},
	{
		id: "comment-1",
		name: "First Words",
		description: "Leave your first comment on a PR or issue",
		category: "communication",
		rarity: "common",
		parentId: undefined,
		status: "available",
		icon: "MessageSquare",
		progress: 0,
		maxProgress: 1,
		unlockedAt: undefined,
	},
];

const meta: Meta<typeof SkillTree> = {
	component: SkillTree,
	parameters: {
		layout: "fullscreen",
	},
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<ReactFlowProvider>
				<div className="h-screen w-full dark bg-background">
					<Story />
				</div>
			</ReactFlowProvider>
		),
	],
};

export default meta;
type Story = StoryObj<typeof SkillTree>;

/**
 * The complete skill tree showing all achievements in their unlocked/available/locked states.
 */
export const Default: Story = {
	args: {
		achievements: mockAchievements,
	},
};

/**
 * Empty state when no achievements are loaded yet.
 */
export const Empty: Story = {
	args: {
		achievements: [],
	},
};

// Achievement Node Stories
const mockUnlockedAchievement: AchievementNodeData = {
	id: "commit-1",
	name: "First Commit",
	description: "Make your first commit to the repository",
	category: "commits",
	tier: "common",
	status: "unlocked",
	icon: "GitCommit",
	progress: 1,
	maxProgress: 1,
	level: 1,
	unlockedAt: new Date("2024-01-15"),
	angle: 270,
	ring: 1,
};

const mockAvailableAchievement: AchievementNodeData = {
	id: "commit-50",
	name: "Commit Specialist",
	description: "Push 50 commits to the repository",
	category: "commits",
	tier: "epic",
	status: "available",
	icon: "GitCommit",
	progress: 38,
	maxProgress: 50,
	level: 5,
	angle: 270,
	ring: 5,
};

const mockLockedAchievement: AchievementNodeData = {
	id: "commit-250",
	name: "Code Veteran",
	description: "Push 250 commits - A true dedication to the codebase",
	category: "commits",
	tier: "legendary",
	status: "locked",
	icon: "Crown",
	progress: 38,
	maxProgress: 250,
	level: 7,
	angle: 270,
	ring: 7,
};

const nodeMetaBase: Meta<typeof AchievementNode> = {
	component: AchievementNode,
	parameters: {
		layout: "centered",
	},
	decorators: [
		(Story) => (
			<ReactFlowProvider>
				<div className="dark bg-background p-12">
					<Story />
				</div>
			</ReactFlowProvider>
		),
	],
};

export const UnlockedNode: StoryObj<typeof AchievementNode> = {
	...nodeMetaBase,
	args: {
		id: mockUnlockedAchievement.id,
		data: mockUnlockedAchievement,
		type: "achievement",
		dragging: false,
		zIndex: 0,
		isConnectable: false,
		positionAbsoluteX: 0,
		positionAbsoluteY: 0,
		deletable: false,
		selectable: false,
		parentId: undefined,
		sourcePosition: undefined,
		targetPosition: undefined,
		selected: false,
		draggable: false,
		width: undefined,
		height: undefined,
	},
};

export const AvailableNode: StoryObj<typeof AchievementNode> = {
	...nodeMetaBase,
	args: {
		id: mockAvailableAchievement.id,
		data: mockAvailableAchievement,
		type: "achievement",
		dragging: false,
		zIndex: 0,
		isConnectable: false,
		positionAbsoluteX: 0,
		positionAbsoluteY: 0,
		deletable: false,
		selectable: false,
		parentId: undefined,
		sourcePosition: undefined,
		targetPosition: undefined,
		selected: false,
		draggable: false,
		width: undefined,
		height: undefined,
	},
};

export const LockedNode: StoryObj<typeof AchievementNode> = {
	...nodeMetaBase,
	args: {
		id: mockLockedAchievement.id,
		data: mockLockedAchievement,
		type: "achievement",
		dragging: false,
		zIndex: 0,
		isConnectable: false,
		positionAbsoluteX: 0,
		positionAbsoluteY: 0,
		deletable: false,
		selectable: false,
		parentId: undefined,
		sourcePosition: undefined,
		targetPosition: undefined,
		selected: false,
		draggable: false,
		width: undefined,
		height: undefined,
	},
};

// Stats Panel Story
export const StatsPanelStory: StoryObj<typeof StatsPanel> = {
	render: () => (
		<div className="dark bg-background h-screen">
			<StatsPanel achievements={mockAchievements} />
		</div>
	),
	parameters: {
		layout: "fullscreen",
	},
};

// Header Story
export const HeaderStory: StoryObj<typeof AchievementHeader> = {
	render: () => (
		<ReactFlowProvider>
			<div className="dark bg-background">
				<AchievementHeader />
			</div>
		</ReactFlowProvider>
	),
	parameters: {
		layout: "fullscreen",
	},
};
