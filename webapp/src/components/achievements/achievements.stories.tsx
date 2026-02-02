import type { Meta, StoryObj } from "@storybook/react";
import { ReactFlowProvider } from "@xyflow/react";
import { AchievementNode } from "./achievement-node";
import type { AchievementNodeData } from "./data";
import { Header } from "./header";
import { SkillTree } from "./skill-tree";
import { StatsPanel } from "./stats-panel";

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
export const Default: Story = {};

// Achievement Node Stories
const mockUnlockedAchievement: AchievementNodeData = {
	id: "commit-1",
	name: "First Commit",
	description: "Make your first commit to the repository",
	category: "commits",
	tier: "minor",
	status: "unlocked",
	icon: "GitCommit",
	requirement: "1 commit",
	progress: 1,
	maxProgress: 1,
	level: 1,
	unlockedAt: "2024-01-15",
	angle: 270,
	ring: 1,
};

const mockAvailableAchievement: AchievementNodeData = {
	id: "commit-50",
	name: "Commit Specialist",
	description: "Push 50 commits to the repository",
	category: "commits",
	tier: "notable",
	status: "available",
	icon: "GitCommit",
	requirement: "50 commits",
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
	requirement: "250 commits",
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
			<StatsPanel />
		</div>
	),
	parameters: {
		layout: "fullscreen",
	},
};

// Header Story
export const HeaderStory: StoryObj<typeof Header> = {
	render: () => (
		<ReactFlowProvider>
			<div className="dark bg-background">
				<Header />
			</div>
		</ReactFlowProvider>
	),
	parameters: {
		layout: "fullscreen",
	},
};
