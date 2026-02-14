import { CheckCircleIcon, XCircleIcon } from "@primer/octicons-react";
import type { Meta, StoryObj } from "@storybook/react";
import { ReactFlowProvider } from "@xyflow/react";
import { AchievementNode } from "@/components/achievements/AchievementNode";
import type { UIAchievement } from "@/components/achievements/types";

/**
 * AchievementNode component for displaying achievements in the skill tree visualization.
 * Shows achievement icons with progress indicators, rarity styling, and tooltips.
 */
const meta = {
	component: AchievementNode,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component: "Displays achievement nodes in the skill tree with digital mythological themes.",
			},
			source: {
				state: "closed",
			},
		},
	},
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<ReactFlowProvider>
				<div
					className="bg-background p-12"
					style={{
						paddingTop: "200px",
						display: "flex",
						justifyContent: "center",
					}}
				>
					<Story />
				</div>
			</ReactFlowProvider>
		),
	],
} satisfies Meta<typeof AchievementNode>;

export default meta;
type Story = StoryObj<typeof meta>;

// Mock achievements with digital mythology theme
const zeusThunderCommits: UIAchievement = {
	id: "pr_beginner",
	name: "Zeus's Thunder Commits",
	description: "Channel the power of lightning to commit code with godly precision",
	icon: CheckCircleIcon,
	category: "commits",
	rarity: "common",
	status: "available",
	unlockedAt: null,
	progressData: {
		type: "LinearAchievementProgress",
		current: 75,
		target: 100,
	},
} as unknown as UIAchievement;

const poseidonCodeStreams: UIAchievement = {
	id: "integration_regular",
	name: "Poseidon's Code Streams",
	description: "Master the tides of continuous integration and deployment",
	icon: CheckCircleIcon,
	category: "issues",
	rarity: "rare",
	status: "unlocked",
	unlockedAt: new Date("2026-02-14T12:00:00Z"),
	progressData: {
		type: "LinearAchievementProgress",
		current: 50,
		target: 50,
	},
} as unknown as UIAchievement;

const athenaWisdomReviews: UIAchievement = {
	id: "first_review",
	name: "Athena's Wisdom Reviews",
	description: "Gain strategic insights through comprehensive code reviews",
	icon: XCircleIcon,
	category: "pull_requests",
	rarity: "epic",
	status: "available",
	unlockedAt: null,
	progressData: {
		type: "LinearAchievementProgress",
		current: 0,
		target: 20,
	},
} as unknown as UIAchievement;

const hermesSwiftDeploys: UIAchievement = {
	id: "review_master",
	name: "Hermes' Swift Deploys",
	description: "Achieve messenger-like speed in deployment cycles",
	icon: CheckCircleIcon,
	category: "milestones",
	rarity: "legendary",
	status: "unlocked",
	unlockedAt: new Date("2026-02-14T13:00:00Z"),
	progressData: {
		type: "BinaryAchievementProgress",
		unlocked: true,
	},
} as unknown as UIAchievement;

const apolloBugFixes: UIAchievement = {
	id: "code_commenter",
	name: "Apollo's Bug Fixes",
	description: "Harness the sun god's clarity to eliminate all software defects",
	icon: XCircleIcon,
	category: "communication",
	rarity: "mythic",
	status: "locked",
	unlockedAt: null,
	progressData: {
		type: "BinaryAchievementProgress",
		unlocked: false,
	},
} as unknown as UIAchievement;

/**
 * Common rarity achievement that is available with progress.
 */
export const CommonAvailable: Story = {
	args: {
		id: zeusThunderCommits.id,
		data: {
			achievement: zeusThunderCommits,
		},
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

/**
 * Rare rarity achievement that is unlocked.
 */
export const RareUnlocked: Story = {
	args: {
		id: poseidonCodeStreams.id,
		data: {
			achievement: poseidonCodeStreams,
		},
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

/**
 * Epic rarity achievement that is available with zero progress.
 */
export const EpicAvailable: Story = {
	args: {
		id: athenaWisdomReviews.id,
		data: {
			achievement: athenaWisdomReviews,
		},
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

/**
 * Legendary rarity achievement that is unlocked (shows pulsing ring).
 */
export const LegendaryUnlocked: Story = {
	args: {
		id: hermesSwiftDeploys.id,
		data: {
			achievement: hermesSwiftDeploys,
		},
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

/**
 * Mythic rarity achievement that is locked.
 */
export const MythicLocked: Story = {
	args: {
		id: apolloBugFixes.id,
		data: {
			achievement: apolloBugFixes,
		},
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
