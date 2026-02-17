import type { Meta, StoryObj } from "@storybook/react";
import { ReactFlowProvider } from "@xyflow/react";
import {
	Cpu,
	Database,
	GitCommit,
	GitPullRequest,
	Globe,
	Shield,
	Terminal,
	Zap
} from "lucide-react";
import type { UIAchievement } from "./types";

// --- Mock Data: Modern Digital Mythology ---

const mockUser = {
	name: "Kratos_Dev",
	avatarUrl: "https://api.dicebear.com/7.x/avataaars/svg?seed=Kratos",
	level: 42,
	leaguePoints: 9001,
};

const mythicAchievements: UIAchievement[] = [
	// COMMITS (Hephaestus's Forge)
	{
		id: "hephaestus-init",
		name: "Hephaestus's Spark",
		description: "Initialize the repository forge with the first commit.",
		category: "commits",
		rarity: "common",
		status: "unlocked",
		icon: Terminal,
		progress: 1,
		maxProgress: 1,
		unlockedAt: new Date("2024-01-01"),
		progressData: { type: "BinaryAchievementProgress", unlocked: true },
	},
	{
		id: "hephaestus-hammer",
		name: "Hammer of CI/CD",
		description: "Forge 50 automated builds without a single failure.",
		category: "commits",
		rarity: "rare",
		parentId: "hephaestus-init",
		status: "unlocked",
		icon: Cpu,
		progress: 50,
		maxProgress: 50,
		unlockedAt: new Date("2024-02-15"),
		progressData: { type: "LinearAchievementProgress", current: 50, target: 50 },
	},
	{
		id: "hephaestus-automaton",
		name: "Golden Automaton",
		description: "Create a self-healing deployment script.",
		category: "commits",
		rarity: "legendary",
		parentId: "hephaestus-hammer",
		status: "available",
		icon: Zap,
		progress: 2,
		maxProgress: 5,
		unlockedAt: null,
		progressData: { type: "LinearAchievementProgress", current: 2, target: 5 },
	},

	// PULL REQUESTS (Hermes's Delivery)
	{
		id: "hermes-sprint",
		name: "Hermes's Hotfix",
		description: "Deliver a hotfix PR in under 10 minutes from issue creation.",
		category: "pull_requests",
		rarity: "uncommon",
		status: "unlocked",
		icon: GitPullRequest,
		progress: 1,
		maxProgress: 1,
		unlockedAt: new Date("2024-03-10"),
		progressData: { type: "BinaryAchievementProgress", unlocked: true },
	},
	{
		id: "hermes-caduceus",
		name: "Caduceus Merger",
		description: "Merge 100 PRs without causing a regression.",
		category: "pull_requests",
		rarity: "epic",
		parentId: "hermes-sprint",
		status: "locked",
		icon: Globe,
		progress: 45,
		maxProgress: 100,
		unlockedAt: null,
		progressData: { type: "LinearAchievementProgress", current: 45, target: 100 },
	},

	// COMMUNICATION (Athena's Wisdom)
	{
		id: "athena-review",
		name: "Owl's Eye Review",
		description: "Provide constructive feedback on 10 junior developer PRs.",
		category: "communication",
		rarity: "rare",
		status: "unlocked",
		icon: GitCommit, // Using generic icon as placeholder
		progress: 10,
		maxProgress: 10,
		unlockedAt: new Date("2024-01-20"),
		progressData: { type: "LinearAchievementProgress", current: 10, target: 10 },
	},
	{
		id: "athena-strategy",
		name: "Architecture Aegis",
		description: "Defend the system architecture in a design review.",
		category: "communication",
		rarity: "mythic",
		parentId: "athena-review",
		status: "locked",
		icon: Shield,
		progress: 0,
		maxProgress: 1,
		unlockedAt: null,
		progressData: { type: "BinaryAchievementProgress", unlocked: false },
	},

	// ISSUES (Zeus's Governance)
	{
		id: "zeus-thunderbolt",
		name: "Thunderbolt Bug Squash",
		description: "Close a critical issue with highest priority.",
		category: "issues",
		rarity: "epic",
		status: "available",
		icon: Zap,
		progress: 0,
		maxProgress: 1,
		unlockedAt: null,
		progressData: { type: "BinaryAchievementProgress", unlocked: false },
	},

	// MILESTONES (Poseidon's Depth)
	{
		id: "poseidon-trident",
		name: "Trident Release",
		description: "Successfully ship 3 major versions.",
		category: "milestones",
		rarity: "legendary",
		status: "unlocked",
		icon: Database,
		progress: 3,
		maxProgress: 3,
		unlockedAt: new Date("2024-04-01"),
		progressData: {
