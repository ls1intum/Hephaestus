import type { Meta, StoryObj } from "@storybook/react";
import { CheckCircleIcon, XCircleIcon } from "@primer/octicons-react";
import { Button } from "@/components/ui/button";
import type { UIAchievement } from "@/components/achievements/types";
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
