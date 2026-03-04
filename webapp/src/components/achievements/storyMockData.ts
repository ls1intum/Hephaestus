import {
	Cpu,
	Database,
	Eye,
	Github,
	GitPullRequest,
	Globe,
	Shield,
	Terminal,
	TrendingUp,
	Zap,
} from "lucide-react";
import type { UIAchievement } from "./types";

export type MockUIAchievement = UIAchievement & {
	x?: number;
	y?: number;
};

export const mockUser = {
	name: "Hephaestus_Forge_Master",
	avatarUrl: "https://github.com/github.png",
	level: 42,
	leaguePoints: 9001,
};

export const hephaestusInit: MockUIAchievement = {
	id: "hephaestus-init",
	name: "Hephaestus's Spark",
	description: "Initialize the repository forge with the first divine commit.",
	category: "commits",
	rarity: "common",
	status: "unlocked",
	icon: Terminal,
	progress: 1,
	maxProgress: 1,
	unlockedAt: new Date("2024-01-01"),
	progressData: { type: "BinaryAchievementProgress", unlocked: true },
	x: 0,
	y: -144,
} as unknown as MockUIAchievement;

export const hephaestusHammer: MockUIAchievement = {
	id: "hephaestus-hammer",
	name: "Hammer of CI/CD",
	description: "Forge 50 automated builds without a single failure in the fires of the pipeline.",
	category: "commits",
	rarity: "rare",
	parentId: "hephaestus-init",
	status: "unlocked",
	icon: Cpu,
	progress: 50,
	maxProgress: 50,
	unlockedAt: new Date("2024-02-15"),
	progressData: { type: "LinearAchievementProgress", current: 50, target: 50 },
	x: 0,
	y: -240,
} as unknown as MockUIAchievement;

export const hephaestusAutomaton: MockUIAchievement = {
	id: "hephaestus-automaton",
	name: "Golden Automaton",
	description: "Create a self-healing deployment script using advanced Prometheus cloud magic.",
	category: "commits",
	rarity: "legendary",
	parentId: "hephaestus-hammer",
	status: "available",
	icon: Zap,
	progress: 2,
	maxProgress: 5,
	unlockedAt: new Date(0),
	progressData: { type: "LinearAchievementProgress", current: 2, target: 5 },
	x: -96,
	y: -384,
} as unknown as MockUIAchievement;

export const apolloClarity: MockUIAchievement = {
	id: "apollo-clarity",
	name: "Apollo's Refactor",
	description: "Bring the sun's clarity to a thousand lines of dark, dusty legacy code.",
	category: "commits",
	rarity: "epic",
	parentId: "hephaestus-hammer",
	status: "available",
	icon: TrendingUp,
	progress: 250,
	maxProgress: 1000,
	progressData: { type: "LinearAchievementProgress", current: 250, target: 1000 },
	x: 96,
	y: -384,
} as unknown as MockUIAchievement;

export const hermesSprint: MockUIAchievement = {
	id: "hermes-sprint",
	name: "Hermes's Hotfix",
	description: "Deliver a critical hotfix with the legendary speed of winged sandals.",
	category: "pull_requests",
	rarity: "uncommon",
	status: "unlocked",
	icon: GitPullRequest,
	progress: 1,
	maxProgress: 1,
	unlockedAt: new Date("2024-03-10"),
	progressData: { type: "BinaryAchievementProgress", unlocked: true },
	x: 144,
	y: 0,
} as unknown as MockUIAchievement;

export const hermesCaduceus: MockUIAchievement = {
	id: "hermes-caduceus",
	name: "Caduceus Merger",
	description: "Merge 100 PRs without causing a single regression in the golden flow.",
	category: "pull_requests",
	rarity: "epic",
	parentId: "hermes-sprint",
	status: "available",
	icon: Globe,
	progress: 45,
	maxProgress: 100,
	unlockedAt: new Date(0),
	progressData: { type: "LinearAchievementProgress", current: 45, target: 100 },
	x: 336,
	y: 0,
} as unknown as MockUIAchievement;

export const aresConflict: MockUIAchievement = {
	id: "ares-conflict",
	name: "Ares's Resolution",
	description: "Triumph in a violent git merge conflict across 10+ core system files.",
	category: "pull_requests",
	rarity: "rare",
	parentId: "hermes-sprint",
	status: "available",
	icon: Github,
	progress: 3,
	maxProgress: 10,
	progressData: { type: "LinearAchievementProgress", current: 3, target: 10 },
	x: 240,
	y: -96,
} as unknown as MockUIAchievement;

export const athenaReview: MockUIAchievement = {
	id: "athena-review",
	name: "Owl's Eye Review",
	description: "Provide constructive wisdom on 10 junior developer pull requests.",
	category: "communication",
	rarity: "rare",
	status: "unlocked",
	icon: Eye,
	progress: 10,
	maxProgress: 10,
	unlockedAt: new Date("2024-01-20"),
	progressData: { type: "LinearAchievementProgress", current: 10, target: 10 },
	x: 0,
	y: 144,
} as unknown as MockUIAchievement;

export const athenaStrategy: MockUIAchievement = {
	id: "athena-strategy",
	name: "Architecture Aegis",
	description:
		"Defend the system architecture in a design review against the titans of technical debt.",
	category: "communication",
	rarity: "mythic",
	parentId: "athena-review",
	status: "unlocked",
	icon: Shield,
	progress: 0,
	maxProgress: 1,
	unlockedAt: new Date(0),
	progressData: { type: "BinaryAchievementProgress", unlocked: true },
	x: 0,
	y: 264,
} as unknown as MockUIAchievement;

export const zeusThunderbolt: MockUIAchievement = {
	id: "zeus-thunderbolt",
	name: "Thunderbolt Squash",
	description: "Strike down a critical security vulnerability with divine lightning precision.",
	category: "issues",
	rarity: "mythic",
	status: "available",
	icon: Zap,
	progress: 0,
	maxProgress: 1,
	progressData: { type: "BinaryAchievementProgress", unlocked: false },
	x: -144,
	y: 0,
} as unknown as MockUIAchievement;

export const poseidonTrident: MockUIAchievement = {
	id: "poseidon-trident",
	name: "Trident Release",
	description: "Successfully ship 3 major oceanic versions of the platform.",
	category: "milestones",
	rarity: "legendary",
	parentId: "poseidon-trident",
	status: "unlocked",
	icon: Database,
	progress: 3,
	maxProgress: 3,
	unlockedAt: new Date("2024-04-01"),
	progressData: { type: "LinearAchievementProgress", current: 3, target: 3 },
	x: 200,
	y: 200,
} as unknown as MockUIAchievement;

export const dionysusDeploy: MockUIAchievement = {
	id: "dionysus-deploy",
	name: "Dionysian Friday Deploy",
	description:
		"Deploy to production on a Friday afternoon without the system collapsing into chaos.",
	category: "milestones",
	rarity: "mythic",
	parentId: "dionysus-deploy",
	status: "locked",
	icon: Zap,
	progress: 0,
	maxProgress: 1,
	progressData: { type: "BinaryAchievementProgress", unlocked: false },
	x: -216,
	y: -216,
} as unknown as MockUIAchievement;

export const mythicAchievements: MockUIAchievement[] = [
	hephaestusInit,
	hephaestusHammer,
	hephaestusAutomaton,
	apolloClarity,
	hermesSprint,
	hermesCaduceus,
	aresConflict,
	athenaReview,
	athenaStrategy,
	zeusThunderbolt,
	poseidonTrident,
	dionysusDeploy,
];
