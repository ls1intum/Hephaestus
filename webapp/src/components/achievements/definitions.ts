import { IssueClosedIcon, IssueOpenedIcon } from "@primer/octicons-react";
import {
	AnvilIcon,
	AtomIcon,
	Clock1Icon,
	ClockFadingIcon,
	DropletsIcon,
	EyeIcon,
	GhostIcon,
	HammerIcon,
	LanguagesIcon,
	LibraryIcon,
	MoonStarIcon,
	PocketKnifeIcon,
	ScanEyeIcon,
	ScanLineIcon,
	SquaresIntersectIcon,
	UsersIcon,
} from "lucide-react";
import { SingularityIcon } from "@/components/achievements/singularity-icon.tsx";
import { defaultCategoryIcons } from "@/components/achievements/styles.ts";
import type { AchievementDisplay, AchievementId } from "@/components/achievements/types.ts";

type PartialAchievementRegistry = Partial<Record<AchievementId, AchievementDisplay>>;

const pullRequestAchievement = {
	"pr.merged.common.1": {
		name: "First Leaf",
		description: "Merge your first Pull Request",
		icon: defaultCategoryIcons.pull_requests,
	},
	"pr.merged.common.2": {
		name: "Branch Grafter",
		description: "Merge 3 Pull Requests in total",
		icon: defaultCategoryIcons.pull_requests,
	},
	"pr.merged.uncommon": {
		name: "Tree Surgeon",
		description: "Merge 5 Pull Requests in total",
		icon: defaultCategoryIcons.pull_requests,
	},
	"pr.merged.rare": {
		name: "Trunk Master",
		description: "Merge 10 Pull Requests in total",
		icon: defaultCategoryIcons.pull_requests,
	},
	"pr.merged.epic": {
		name: "Forest Keeper",
		description: "Merge 25 Pull Requests",
		icon: defaultCategoryIcons.pull_requests,
	},
	"pr.merged.legendary": {
		name: "Root of Origin",
		description: "Merge 50 Pull Requests",
		icon: defaultCategoryIcons.pull_requests,
	},
	"pr.special.speedster": {
		name: "Speedster",
		description: "Open a PR and close it in 5 minutes or less",
		icon: Clock1Icon,
	},
} satisfies PartialAchievementRegistry;

const commitAchievements = {
	"commit.common.1": {
		name: '"Hello World!"',
		description: "Commit your first code changes",
		icon: defaultCategoryIcons.commits,
	},
	"commit.common.2": {
		name: "Data Fragment",
		description: "Push 10 commits in total",
		icon: defaultCategoryIcons.commits,
	},
	"commit.uncommon.1": {
		name: "Version Controller",
		description: "Push 50 commits in total",
		icon: defaultCategoryIcons.commits,
	},
	"commit.uncommon.2": {
		name: "Continuous Integrator",
		description: "Push 100 commits in total",
		icon: defaultCategoryIcons.commits,
	},
	"commit.rare": {
		name: "System Shaper",
		description: "Push 250 commits in total",
		icon: defaultCategoryIcons.commits,
	},
	"commit.epic": {
		name: "Core Constructor",
		description: "Push 500 commits in total",
		icon: defaultCategoryIcons.commits,
	},
	"commit.legendary": {
		name: "Master Builder",
		description: "Push 1000 commits in total",
		icon: defaultCategoryIcons.commits,
	},
	"commit.mythic": {
		name: "The Forges Flames",
		description: "Push 2000 commits in total",
		icon: AnvilIcon,
	},
	"commit.special.itsy_bitsy": {
		name: "Itsy Bitsy ...",
		description: "Push a commit with only one line of code changed",
		icon: ScanLineIcon,
	},
	"commit.special.atomic_changes": {
		name: "Atomic Reconstruction",
		description: "Push 10 commits in a row that change at most 3 lines of code in at most 2 files.",
		icon: AtomIcon,
	},
	"commit.special.brute_force": {
		name: "Brute Force",
		description: "Commit 5 times in 5 minutes",
		icon: HammerIcon,
	},
	"commit.special.cross_boundary": {
		name: "Cross-Boundary Dev",
		description:
			"Push a commit that changes files with at least 2 different programming languages.",
		icon: SquaresIntersectIcon,
	},
} satisfies PartialAchievementRegistry;

const communicationAchievements = {
	"review.common.1": {
		name: "Peer Check",
		description: "Write a review on a PR not authored by yourself",
		icon: defaultCategoryIcons.communication,
	},
	"review.common.2": {
		name: "Feedback loop",
		description: "Write at least 10 reviews on PRs not authored by yourself",
		icon: defaultCategoryIcons.communication,
	},
	"review.uncommon.1": {
		name: "Sanity Checker",
		description: "Write at least 25 reviews on PRs not authored by yourself",
		icon: defaultCategoryIcons.communication,
	},
	"review.uncommon.2": {
		name: "Review Councillor",
		description: "Write at least 50 reviews on PRs not authored by yourself",
		icon: defaultCategoryIcons.communication,
	},
	"review.rare": {
		name: "Quality Sentinel",
		description: "Write at least 100 reviews on PRs not authored by yourself",
		icon: defaultCategoryIcons.communication,
	},
	"review.epic": {
		name: "Principle Mentor",
		description: "Write at least 200 reviews on PRs not authored by yourself",
		icon: defaultCategoryIcons.communication,
	},
	"review.legendary": {
		name: "Architecture Filter",
		description: "Write at least 500 reviews on PRs not authored by yourself",
		icon: defaultCategoryIcons.communication,
	},
	"review.mythic": {
		name: "All Seeing Eye",
		description: "Write at least 1000 reviews on PRs not authored by yourself",
		icon: ScanEyeIcon,
	},
} satisfies PartialAchievementRegistry;

const issueAchievement = {
	"issue.open.common.1": {
		name: "Houston, We Have a Problem!",
		description: "Open an issue",
		icon: IssueOpenedIcon,
	},
	"issue.open.common.2": {
		name: "Junior Scout",
		description: "Open a total of 5 issues",
		icon: IssueOpenedIcon,
	},
	"issue.open.uncommon": {
		name: "Bug Tracer",
		description: "Open a total of 10 issues",
		icon: IssueOpenedIcon,
	},
	"issue.open.rare": {
		name: "Backlog Influencer",
		description: "Open a total of 15 issues",
		icon: IssueOpenedIcon,
	},
	"issue.open.epic": {
		name: "Issue Architect",
		description: "Open a total of 30 issues",
		icon: IssueOpenedIcon,
	},
	"issue.open.legendary": {
		name: "Chief Complaining Officer",
		description: "Open a total of 50 issues",
		icon: IssueOpenedIcon,
	},
	"issue.close.common.1": {
		name: "Take That, Bug!",
		description: "Close an issues",
		icon: IssueClosedIcon,
	},
	"issue.close.common.2": {
		name: "Repository Janitor",
		description: "Close a total of 5 issues",
		icon: IssueClosedIcon,
	},
	"issue.close.uncommon": {
		name: "Firefighter",
		description: "Close a total of 10 issues",
		icon: IssueClosedIcon,
	},
	"issue.close.rare": {
		name: "Bug Reaper",
		description: "Close a total of 15 issues",
		icon: IssueClosedIcon,
	},
	"issue.close.epic": {
		name: "Pest Doctor",
		description: "Close a total of 30 issues",
		icon: IssueClosedIcon,
	},
	"issue.close.legendary": {
		name: "Terminator",
		description: "Close a total of 50 issues",
		icon: IssueClosedIcon,
	},
	"issue.special.hive_mind": {
		name: "Hive Mind",
		description: "Close an issue with 10 or more unique participants",
		icon: UsersIcon,
	},
	"issue.special.necromancer": {
		name: "Necromancer",
		description: "Open an issue and close it by yourself without anyone else interacting with it",
		icon: GhostIcon,
	},
	"issue.special.oracle": {
		name: "Oracle",
		description: "Close an issue that has been open for over 6 month",
		icon: EyeIcon,
	},
} satisfies PartialAchievementRegistry;

const milestoneAchievements = {
	"milestone.first_action": {
		name: "First Blood",
		description: "Do your first action in a workspace",
		icon: DropletsIcon,
	},
	"milestone.polyglot": {
		name: "Polyglot",
		description: "Commit in 3 different programming languages in total",
		icon: LanguagesIcon,
	},
	"milestone.night_owl": {
		name: "Night Owl",
		description: "Commit 5 times between 1-5 a.m. on the same day",
		icon: MoonStarIcon,
	},
	"milestone.long_time_return": {
		name: "The Ancient One",
		description: "Return after 3 months of inactivity",
		icon: ClockFadingIcon,
	},
	"milestone.all_rare": {
		name: "Swiss Army Knife",
		description: "Unlock the rare achievement in every major category line",
		icon: PocketKnifeIcon,
	},
	"milestone.all_epic": {
		name: "Librarian of Alexandria",
		description: "Unlock the epic achievement in every major category line",
		icon: LibraryIcon,
		forceAura: true,
	},
	"milestone.all_legendary": {
		name: "Singularity",
		description: "Unlock the legendary achievement in every major category line",
		icon: SingularityIcon,
		forceAura: true,
	},
} satisfies PartialAchievementRegistry;

export const ACHIEVEMENT_REGISTRY = {
	...pullRequestAchievement,
	...commitAchievements,
	...communicationAchievements,
	...issueAchievement,
	...milestoneAchievements,
} as const satisfies Record<AchievementId, AchievementDisplay>;
