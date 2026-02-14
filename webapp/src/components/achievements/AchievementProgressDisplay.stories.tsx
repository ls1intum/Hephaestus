import type { Meta, StoryObj } from "@storybook/react";
import type { Achievement } from "@/api/types.gen";
import { AchievementProgressDisplay } from "./AchievementProgressDisplay";

/**
 * Component for displaying achievement progress with different progress types.
 * Supports linear progress bars and binary status indicators.
 */
const meta = {
	component: AchievementProgressDisplay,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component: "Displays progress for achievements in digital mythological themes.",
			},
		},
	},
	tags: ["autodocs"],
} satisfies Meta<typeof AchievementProgressDisplay>;

export default meta;
type Story = StoryObj<typeof meta>;

// Mock achievements with digital mythology theme
const linearEmptyAchievement: Achievement = {
	id: "first_review",
	name: "Athena's Wisdom Reviews",
	description: "Gain strategic insights through comprehensive code reviews",
	category: "pull_requests",
	rarity: "epic",
	status: "available",
	unlockedAt: null,
	progressData: {
		type: "LinearAchievementProgress",
		current: 0,
		target: 20,
	},
} as unknown as Achievement;

const linearPartialAchievement: Achievement = {
	id: "pr_beginner",
	name: "Zeus's Thunder Commits",
	description: "Channel the power of lightning to commit code with godly precision",
	category: "commits",
	rarity: "common",
	status: "available",
	unlockedAt: null,
	progressData: {
		type: "LinearAchievementProgress",
		current: 75,
		target: 100,
	},
} as unknown as Achievement;

const linearCompleteAchievement: Achievement = {
	id: "integration_regular",
	name: "Poseidon's Code Streams",
	description: "Master the tides of continuous integration and deployment",
	category: "issues",
	rarity: "rare",
	status: "available",
	unlockedAt: new Date("2026-02-14T12:00:00Z"),
	progressData: {
		type: "LinearAchievementProgress",
		current: 50,
		target: 50,
	},
} as unknown as Achievement;

const binaryUnlockedAchievement: Achievement = {
	id: "review_master",
	name: "Hermes' Swift Deploys",
	description: "Achieve messenger-like speed in deployment cycles",
	category: "milestones",
	rarity: "legendary",
	status: "unlocked",
	unlockedAt: new Date("2026-02-14T13:00:00Z"),
	progressData: {
		type: "BinaryAchievementProgress",
		unlocked: true,
	},
} as unknown as Achievement;

const binaryLockedAchievement: Achievement = {
	id: "code_commenter",
	name: "Apollo's Bug Fixes",
	description: "Harness the sun god's clarity to eliminate all software defects",
	category: "communication",
	rarity: "mythic",
	status: "available",
	unlockedAt: null,
	progressData: {
		type: "BinaryAchievementProgress",
		unlocked: false,
	},
} as unknown as Achievement;

/**
 * Linear achievement with no progress (0% complete).
 */
export const LinearEmpty: Story = {
	args: {
		achievement: linearEmptyAchievement,
	},
};

/**
 * Linear achievement with partial progress (75% complete).
 */
export const LinearPartial: Story = {
	args: {
		achievement: linearPartialAchievement,
	},
};

/**
 * Linear achievement with complete progress (100% complete).
 */
export const LinearComplete: Story = {
	args: {
		achievement: linearCompleteAchievement,
	},
};

/**
 * Binary achievement that is unlocked.
 */
export const BinaryUnlocked: Story = {
	args: {
		achievement: binaryUnlockedAchievement,
	},
};

/**
 * Binary achievement that is locked.
 */
export const BinaryLocked: Story = {
	args: {
		achievement: binaryLockedAchievement,
	},
};
