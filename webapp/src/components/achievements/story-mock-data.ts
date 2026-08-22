import {
	Cpu,
	Database,
	Eye,
	GitPullRequest,
	Globe,
	Shield,
	Terminal,
	TrendingUp,
	Zap,
} from "lucide-react";
import type { UIAchievement } from "@/components/achievements/types";
import { Github } from "@/components/icons/brand";

/**
 * A real {@link UIAchievement} plus the coordinates the skill-tree stories pin nodes at, so a story
 * lays out a tree without the layout pass. The ids are the catalogue's own: the tree keys nodes and
 * edges by id, and an invented one would draw a tree the app can never render.
 */
export type MockUIAchievement = UIAchievement & {
	x?: number;
	y?: number;
};

/**
 * What an achievement nobody has earned looks like on the client: the server omits `unlockedAt`,
 * and the generated response transformer turns that absence into an invalid Date.
 */
const NEVER_UNLOCKED = new Date(Number.NaN);

export const mockUser = {
	name: "Hephaestus_Forge_Master",
	avatarUrl: "https://github.com/github.png",
	level: 42,
	leaguePoints: 9001,
};

export const hephaestusInit: MockUIAchievement = {
	id: "commit.common.1",
	name: "Hephaestus's Spark",
	description: "Initialize the repository forge with the first divine commit.",
	category: "commits",
	rarity: "common",
	status: "unlocked",
	isHidden: false,
	icon: Terminal,
	unlockedAt: new Date("2024-01-01"),
	progressData: { type: "BinaryAchievementProgress", unlocked: true },
	x: 0,
	y: -144,
};

const hephaestusHammer: MockUIAchievement = {
	id: "commit.rare",
	name: "Hammer of CI/CD",
	description: "Forge 50 automated builds without a single failure in the fires of the pipeline.",
	category: "commits",
	rarity: "rare",
	parent: "commit.common.1",
	status: "unlocked",
	isHidden: false,
	icon: Cpu,
	unlockedAt: new Date("2024-02-15"),
	progressData: { type: "LinearAchievementProgress", current: 50, target: 50 },
	x: 0,
	y: -240,
};

const hephaestusAutomaton: MockUIAchievement = {
	id: "commit.legendary",
	name: "Golden Automaton",
	description: "Create a self-healing deployment script using advanced Prometheus cloud magic.",
	category: "commits",
	rarity: "legendary",
	parent: "commit.rare",
	status: "available",
	isHidden: false,
	icon: Zap,
	unlockedAt: NEVER_UNLOCKED,
	progressData: { type: "LinearAchievementProgress", current: 2, target: 5 },
	x: -96,
	y: -384,
};

export const apolloClarity: MockUIAchievement = {
	id: "commit.epic",
	name: "Apollo's Refactor",
	description: "Bring the sun's clarity to a thousand lines of dark, dusty legacy code.",
	category: "commits",
	rarity: "epic",
	parent: "commit.rare",
	status: "available",
	isHidden: false,
	icon: TrendingUp,
	unlockedAt: NEVER_UNLOCKED,
	progressData: { type: "LinearAchievementProgress", current: 250, target: 1000 },
	x: 96,
	y: -384,
};

export const hermesSprint: MockUIAchievement = {
	id: "pr.merged.uncommon",
	name: "Hermes's Hotfix",
	description: "Deliver a critical hotfix with the legendary speed of winged sandals.",
	category: "pull_requests",
	rarity: "uncommon",
	status: "unlocked",
	isHidden: false,
	icon: GitPullRequest,
	unlockedAt: new Date("2024-03-10"),
	progressData: { type: "BinaryAchievementProgress", unlocked: true },
	x: 144,
	y: 0,
};

const hermesCaduceus: MockUIAchievement = {
	id: "pr.merged.epic",
	name: "Caduceus Merger",
	description: "Merge 100 PRs without causing a single regression in the golden flow.",
	category: "pull_requests",
	rarity: "epic",
	parent: "pr.merged.uncommon",
	status: "available",
	isHidden: false,
	icon: Globe,
	unlockedAt: NEVER_UNLOCKED,
	progressData: { type: "LinearAchievementProgress", current: 45, target: 100 },
	x: 336,
	y: 0,
};

export const aresConflict: MockUIAchievement = {
	id: "pr.merged.rare",
	name: "Ares's Resolution",
	description: "Triumph in a violent git merge conflict across 10+ core system files.",
	category: "pull_requests",
	rarity: "rare",
	parent: "pr.merged.uncommon",
	status: "available",
	isHidden: false,
	icon: Github,
	unlockedAt: NEVER_UNLOCKED,
	progressData: { type: "LinearAchievementProgress", current: 3, target: 10 },
	x: 240,
	y: -96,
};

export const athenaReview: MockUIAchievement = {
	id: "review.rare",
	name: "Owl's Eye Review",
	description: "Provide constructive wisdom on 10 junior developer pull requests.",
	category: "communication",
	rarity: "rare",
	status: "unlocked",
	isHidden: false,
	icon: Eye,
	unlockedAt: new Date("2024-01-20"),
	progressData: { type: "LinearAchievementProgress", current: 10, target: 10 },
	x: 0,
	y: 144,
};

const athenaStrategy: MockUIAchievement = {
	id: "review.mythic",
	name: "Architecture Aegis",
	description:
		"Defend the system architecture in a design review against the titans of technical debt.",
	category: "communication",
	rarity: "mythic",
	parent: "review.rare",
	status: "unlocked",
	isHidden: false,
	icon: Shield,
	unlockedAt: NEVER_UNLOCKED,
	progressData: { type: "BinaryAchievementProgress", unlocked: true },
	x: 0,
	y: 264,
};

export const zeusThunderbolt: MockUIAchievement = {
	id: "issue.special.oracle",
	name: "Thunderbolt Squash",
	description: "Strike down a critical security vulnerability with divine lightning precision.",
	category: "issues",
	rarity: "mythic",
	status: "available",
	isHidden: false,
	icon: Zap,
	unlockedAt: NEVER_UNLOCKED,
	progressData: { type: "BinaryAchievementProgress", unlocked: false },
	x: -144,
	y: 0,
};

export const poseidonTrident: MockUIAchievement = {
	id: "milestone.all_legendary",
	name: "Trident Release",
	description: "Successfully ship 3 major oceanic versions of the platform.",
	category: "milestones",
	rarity: "legendary",
	status: "unlocked",
	isHidden: false,
	icon: Database,
	unlockedAt: new Date("2024-04-01"),
	progressData: { type: "LinearAchievementProgress", current: 3, target: 3 },
	x: 200,
	y: 200,
};

export const dionysusDeploy: MockUIAchievement = {
	id: "milestone.polyglot",
	name: "Dionysian Friday Deploy",
	description:
		"Deploy to production on a Friday afternoon without the system collapsing into chaos.",
	category: "milestones",
	rarity: "mythic",
	status: "locked",
	isHidden: false,
	icon: Zap,
	unlockedAt: NEVER_UNLOCKED,
	progressData: { type: "BinaryAchievementProgress", unlocked: false },
	x: -216,
	y: -216,
};

export const artemisHunt: MockUIAchievement = {
	id: "issue.close.rare",
	name: "Artemis's Hunt",
	description: "Track and resolve 25 stale issues hiding in the wilderness of the backlog.",
	category: "issues",
	rarity: "rare",
	status: "available",
	isHidden: false,
	icon: Eye,
	unlockedAt: NEVER_UNLOCKED,
	progressData: { type: "LinearAchievementProgress", current: 0, target: 25 },
	x: -240,
	y: 96,
};

export const prometheusLongName: MockUIAchievement = {
	id: "milestone.all_epic",
	name: "Prometheus's Extraordinarily Magnificent Celestial Fire-Stealing Code Contribution Achievement of Legendary Proportions",
	description:
		"Complete an unprecedented series of contributions spanning multiple repositories, frameworks, and paradigms to demonstrate mastery over the entire development lifecycle from inception to deployment and beyond.",
	category: "milestones",
	rarity: "epic",
	status: "unlocked",
	isHidden: false,
	icon: Zap,
	unlockedAt: new Date("2024-06-15"),
	progressData: { type: "BinaryAchievementProgress", unlocked: true },
	x: -300,
	y: 200,
};

export const hadesSecret: MockUIAchievement = {
	id: "milestone.night_owl",
	name: "???",
	description: "This achievement is hidden. Complete specific tasks to reveal it.",
	category: "milestones",
	rarity: "legendary",
	status: "hidden",
	isHidden: true,
	icon: Shield,
	unlockedAt: NEVER_UNLOCKED,
	progressData: { type: "BinaryAchievementProgress", unlocked: false },
	x: -300,
	y: -200,
};

const mythicAchievements: MockUIAchievement[] = [
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

/** Drops the story-only coordinates, for args typed as a plain `UIAchievement`. */
export function asUI(mock: MockUIAchievement): UIAchievement {
	return mock;
}

/** For stories that accept UIAchievement[]. */
export const mythicAchievementsUI: UIAchievement[] = mythicAchievements.map(asUI);
