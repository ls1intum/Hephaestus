import type { Meta, StoryObj } from "@storybook/react";
import type { AchievementNodeData } from "@/components/achievements/data";
import { Button } from "@/components/ui/button";
import { AchievementTooltip } from "./AchievementTooltip";

/**
 * Component for displaying achievement tooltips with detailed information.
 * Shows achievement name, tier, description, progress, and unlock date when available.
 */
const meta = {
	component: AchievementTooltip,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component: "Displays tooltips for achievements in digital mythological themes.",
			},
			source: {
				state: "closed",
			},
		},
	},
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<div
				style={{
					paddingTop: "200px",
					display: "flex",
					justifyContent: "center",
				}}
			>
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof AchievementTooltip>;

export default meta;
type Story = StoryObj<typeof meta>;

// Mock achievement node data with digital mythology theme
const zeusThunderCommits: AchievementNodeData = {
	id: "pr_beginner",
	name: "Zeus's Thunder Commits",
	description: "Channel the power of lightning to commit code with godly precision",
	category: "commits",
	tier: "common",
	status: "available",
	icon: "GitCommit",
	progress: 75,
	maxProgress: 100,
	unlockedAt: null,
	level: 1,
	angle: 270,
	ring: 1,
} as unknown as AchievementNodeData;

const poseidonCodeStreams: AchievementNodeData = {
	id: "integration_regular",
	name: "Poseidon's Code Streams",
	description: "Master the tides of continuous integration and deployment",
	category: "issues",
	tier: "rare",
	status: "unlocked",
	icon: "Workflow",
	progress: 50,
	maxProgress: 50,
	unlockedAt: new Date("2026-02-14T12:00:00Z"),
	level: 3,
	angle: 126,
	ring: 3,
} as unknown as AchievementNodeData;

const athenaWisdomReviews: AchievementNodeData = {
	id: "first_review",
	name: "Athena's Wisdom Reviews",
	description: "Gain strategic insights through comprehensive code reviews",
	category: "pull_requests",
	tier: "epic",
	status: "available",
	icon: "GitPullRequest",
	progress: 0,
	maxProgress: 20,
	unlockedAt: null,
	level: 4,
	angle: 342,
	ring: 4,
} as unknown as AchievementNodeData;

const hermesSwiftDeploys: AchievementNodeData = {
	id: "review_master",
	name: "Hermes' Swift Deploys",
	description: "Achieve messenger-like speed in deployment cycles",
	category: "milestones",
	tier: "legendary",
	status: "unlocked",
	icon: "Rocket",
	unlockedAt: new Date("2026-02-14T13:00:00Z"),
	level: 5,
	angle: 198,
	ring: 5,
} as unknown as AchievementNodeData;

const apolloBugFixes: AchievementNodeData = {
	id: "code_commenter",
	name: "Apollo's Bug Fixes",
	description: "Harness the sun god's clarity to eliminate all software defects",
	category: "communication",
	tier: "mythic",
	status: "locked",
	icon: "Bug",
	unlockedAt: null,
	level: 6,
	angle: 54,
	ring: 6,
} as unknown as AchievementNodeData;

/**
 * Tooltip for an available achievement with zero progress.
 */
export const AvailableEmptyProgress: Story = {
	args: {
		achievement: athenaWisdomReviews,
		open: true,
		children: <Button>Achievement</Button>,
	},
};

/**
 * Tooltip for an available achievement with partial progress.
 */
export const AvailableWithProgress: Story = {
	args: {
		achievement: zeusThunderCommits,
		open: true,
		children: <Button>Achievement</Button>,
	},
};

/**
 * Tooltip for an unlocked achievement with complete progress.
 */
export const UnlockedWithProgress: Story = {
	args: {
		achievement: poseidonCodeStreams,
		open: true,
		children: <Button>Achievement</Button>,
	},
};

/**
 * Tooltip for an unlocked achievement without progress (binary achievement).
 */
export const UnlockedBinary: Story = {
	args: {
		achievement: hermesSwiftDeploys,
		open: true,
		children: <Button>Achievement</Button>,
	},
};

/**
 * Tooltip for a locked achievement.
 */
export const Locked: Story = {
	args: {
		achievement: apolloBugFixes,
		open: true,
		children: <Button>Achievement</Button>,
	},
};
