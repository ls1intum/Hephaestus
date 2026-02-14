import { CheckCircleIcon, XCircleIcon } from "@primer/octicons-react";
import type { Meta, StoryObj } from "@storybook/react";
import type { UIAchievement } from "@/components/achievements/types";
import { AchievementsListView } from "./AchievementsListView";

/**
 * Component for displaying achievements in an accessible list/table format.
 * Groups achievements by category and shows progress, status, and details.
 */
const meta = {
	component: AchievementsListView,
	parameters: {
		layout: "fullscreen",
		docs: {
			description: {
				component: "Displays achievements in a tabular format with digital mythological themes.",
			},
		},
	},
	tags: ["autodocs"],
} satisfies Meta<typeof AchievementsListView>;

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

const demeterHarvestMerges: UIAchievement = {
	id: "helpful_reviewer",
	name: "Demeter's Harvest Merges",
	description: "Cultivate bountiful code through nurturing pull request reviews",
	icon: CheckCircleIcon,
	category: "pull_requests",
	rarity: "uncommon",
	status: "unlocked",
	unlockedAt: new Date("2026-02-14T14:00:00Z"),
	progressData: {
		type: "LinearAchievementProgress",
		current: 15,
		target: 15,
	},
} as unknown as UIAchievement;

const aresConflictResolver: UIAchievement = {
	id: "pr_specialist",
	name: "Ares' Conflict Resolver",
	description: "Battle through merge conflicts with warrior-like determination",
	icon: XCircleIcon,
	category: "pull_requests",
	rarity: "rare",
	status: "available",
	unlockedAt: null,
	progressData: {
		type: "LinearAchievementProgress",
		current: 3,
		target: 10,
	},
} as unknown as UIAchievement;

/**
 * Empty list when no achievements are available.
 */
export const Empty: Story = {
	args: {
		achievements: [],
	},
};

/**
 * List with achievements in mixed statuses (unlocked, available, locked).
 */
export const MixedStatuses: Story = {
	args: {
		achievements: [
			zeusThunderCommits,
			poseidonCodeStreams,
			athenaWisdomReviews,
			hermesSwiftDeploys,
			apolloBugFixes,
		],
	},
};

/**
 * List showing only unlocked achievements.
 */
export const AllUnlocked: Story = {
	args: {
		achievements: [poseidonCodeStreams, hermesSwiftDeploys, demeterHarvestMerges],
	},
};

/**
 * List with multiple achievements in the same category.
 */
export const MultipleInCategory: Story = {
	args: {
		achievements: [athenaWisdomReviews, demeterHarvestMerges, aresConflictResolver],
	},
};

/**
 * List with a single achievement.
 */
export const SingleAchievement: Story = {
	args: {
		achievements: [hermesSwiftDeploys],
	},
};
