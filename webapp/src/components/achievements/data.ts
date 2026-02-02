import type { Edge, Node } from "@xyflow/react";

export type AchievementCategory =
	| "commits"
	| "pullRequests"
	| "reviews"
	| "issues"
	| "comments"
	| "crossCategory";

export type AchievementTier = "minor" | "notable" | "keystone" | "legendary";

export type AchievementStatus = "locked" | "available" | "unlocked";

export interface Achievement {
	id: string;
	name: string;
	description: string;
	category: AchievementCategory;
	tier: AchievementTier;
	status: AchievementStatus;
	icon: string;
	requirement: string;
	progress?: number;
	maxProgress?: number;
	unlockedAt?: string;
	// For progressive achievements
	level?: number;
	// For cross-category achievements - which categories they connect
	connects?: AchievementCategory[];
	// Whether this is a hidden/secret achievement
	hidden?: boolean;
}

export interface AchievementNodeData extends Achievement {
	angle: number;
	ring: number;
	// Index signature required by React Flow's Node<T> constraint
	[key: string]: unknown;
}

// Category metadata - 5 main lines radiating from center (72 degrees apart)
export const categoryMeta: Record<
	AchievementCategory,
	{ name: string; angle: number; description: string }
> = {
	commits: {
		name: "Commits",
		angle: 270, // Top (12 o'clock)
		description: "Track your code contributions",
	},
	pullRequests: {
		name: "Pull Requests",
		angle: 342, // Top-right (2 o'clock)
		description: "Submit and merge code changes",
	},
	reviews: {
		name: "Reviews",
		angle: 54, // Right (4 o'clock)
		description: "Help improve code quality",
	},
	issues: {
		name: "Issues",
		angle: 126, // Bottom-right (7 o'clock)
		description: "Report and track work items",
	},
	comments: {
		name: "Comments",
		angle: 198, // Bottom-left (9 o'clock)
		description: "Engage in discussions",
	},
	crossCategory: {
		name: "Milestones",
		angle: 0, // Distributed between branches
		description: "Combined achievements",
	},
};

// ============================================
// MAIN ACHIEVEMENT LINES (Progressive)
// ============================================

// COMMITS LINE - Top branch (270 degrees)
const commitsLine: Achievement[] = [
	{
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
	},
	{
		id: "commit-5",
		name: "Getting Started",
		description: "Push 5 commits to the repository",
		category: "commits",
		tier: "minor",
		status: "unlocked",
		icon: "GitCommit",
		requirement: "5 commits",
		progress: 5,
		maxProgress: 5,
		level: 2,
		unlockedAt: "2024-01-18",
	},
	{
		id: "commit-10",
		name: "Commit Apprentice",
		description: "Push 10 commits to the repository",
		category: "commits",
		tier: "minor",
		status: "unlocked",
		icon: "GitCommit",
		requirement: "10 commits",
		progress: 10,
		maxProgress: 10,
		level: 3,
		unlockedAt: "2024-01-25",
	},
	{
		id: "commit-25",
		name: "Regular Contributor",
		description: "Push 25 commits to the repository",
		category: "commits",
		tier: "notable",
		status: "unlocked",
		icon: "GitCommit",
		requirement: "25 commits",
		progress: 25,
		maxProgress: 25,
		level: 4,
		unlockedAt: "2024-02-10",
	},
	{
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
	},
	{
		id: "commit-100",
		name: "Centurion",
		description: "Push 100 commits to the repository",
		category: "commits",
		tier: "keystone",
		status: "locked",
		icon: "Zap",
		requirement: "100 commits",
		progress: 38,
		maxProgress: 100,
		level: 6,
	},
	{
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
	},
];

// PULL REQUESTS LINE - Top-right branch (342 degrees)
const pullRequestsLine: Achievement[] = [
	{
		id: "pr-1",
		name: "First Pull",
		description: "Open your first pull request",
		category: "pullRequests",
		tier: "minor",
		status: "unlocked",
		icon: "GitPullRequest",
		requirement: "1 PR opened",
		progress: 1,
		maxProgress: 1,
		level: 1,
		unlockedAt: "2024-01-16",
	},
	{
		id: "pr-3",
		name: "PR Beginner",
		description: "Get 3 pull requests merged",
		category: "pullRequests",
		tier: "minor",
		status: "unlocked",
		icon: "GitMerge",
		requirement: "3 PRs merged",
		progress: 3,
		maxProgress: 3,
		level: 2,
		unlockedAt: "2024-01-22",
	},
	{
		id: "pr-5",
		name: "PR Apprentice",
		description: "Get 5 pull requests merged",
		category: "pullRequests",
		tier: "minor",
		status: "unlocked",
		icon: "GitMerge",
		requirement: "5 PRs merged",
		progress: 5,
		maxProgress: 5,
		level: 3,
		unlockedAt: "2024-02-01",
	},
	{
		id: "pr-10",
		name: "Integration Regular",
		description: "Get 10 pull requests merged",
		category: "pullRequests",
		tier: "notable",
		status: "unlocked",
		icon: "GitMerge",
		requirement: "10 PRs merged",
		progress: 10,
		maxProgress: 10,
		level: 4,
		unlockedAt: "2024-02-20",
	},
	{
		id: "pr-25",
		name: "PR Specialist",
		description: "Get 25 pull requests merged",
		category: "pullRequests",
		tier: "notable",
		status: "available",
		icon: "GitMerge",
		requirement: "25 PRs merged",
		progress: 18,
		maxProgress: 25,
		level: 5,
	},
	{
		id: "pr-50",
		name: "Integration Expert",
		description: "Get 50 pull requests merged",
		category: "pullRequests",
		tier: "keystone",
		status: "locked",
		icon: "Rocket",
		requirement: "50 PRs merged",
		progress: 18,
		maxProgress: 50,
		level: 6,
	},
	{
		id: "pr-100",
		name: "Master Integrator",
		description: "Get 100 pull requests merged - A pillar of the team",
		category: "pullRequests",
		tier: "legendary",
		status: "locked",
		icon: "Building",
		requirement: "100 PRs merged",
		progress: 18,
		maxProgress: 100,
		level: 7,
	},
];

// REVIEWS LINE - Right branch (54 degrees)
const reviewsLine: Achievement[] = [
	{
		id: "review-1",
		name: "First Review",
		description: "Submit your first code review",
		category: "reviews",
		tier: "minor",
		status: "unlocked",
		icon: "Eye",
		requirement: "1 review",
		progress: 1,
		maxProgress: 1,
		level: 1,
		unlockedAt: "2024-01-18",
	},
	{
		id: "review-3",
		name: "Review Beginner",
		description: "Submit 3 code reviews",
		category: "reviews",
		tier: "minor",
		status: "unlocked",
		icon: "Eye",
		requirement: "3 reviews",
		progress: 3,
		maxProgress: 3,
		level: 2,
		unlockedAt: "2024-01-24",
	},
	{
		id: "review-5",
		name: "Review Apprentice",
		description: "Submit 5 code reviews",
		category: "reviews",
		tier: "minor",
		status: "unlocked",
		icon: "Eye",
		requirement: "5 reviews",
		progress: 5,
		maxProgress: 5,
		level: 3,
		unlockedAt: "2024-02-05",
	},
	{
		id: "review-10",
		name: "Watchful Eye",
		description: "Submit 10 code reviews",
		category: "reviews",
		tier: "notable",
		status: "unlocked",
		icon: "Shield",
		requirement: "10 reviews",
		progress: 10,
		maxProgress: 10,
		level: 4,
		unlockedAt: "2024-02-15",
	},
	{
		id: "review-25",
		name: "Quality Guardian",
		description: "Submit 25 code reviews",
		category: "reviews",
		tier: "notable",
		status: "available",
		icon: "Shield",
		requirement: "25 reviews",
		progress: 19,
		maxProgress: 25,
		level: 5,
	},
	{
		id: "review-50",
		name: "Code Sage",
		description: "Submit 50 code reviews",
		category: "reviews",
		tier: "keystone",
		status: "locked",
		icon: "Sparkles",
		requirement: "50 reviews",
		progress: 19,
		maxProgress: 50,
		level: 6,
	},
	{
		id: "review-100",
		name: "The Gatekeeper",
		description: "Submit 100 code reviews - The team's quality champion",
		category: "reviews",
		tier: "legendary",
		status: "locked",
		icon: "Lock",
		requirement: "100 reviews",
		progress: 19,
		maxProgress: 100,
		level: 7,
	},
];

// ISSUES LINE - Bottom-right branch (126 degrees)
const issuesLine: Achievement[] = [
	{
		id: "issue-1",
		name: "First Issue",
		description: "Create your first issue",
		category: "issues",
		tier: "minor",
		status: "unlocked",
		icon: "CircleDot",
		requirement: "1 issue created",
		progress: 1,
		maxProgress: 1,
		level: 1,
		unlockedAt: "2024-01-17",
	},
	{
		id: "issue-3",
		name: "Issue Reporter",
		description: "Create 3 issues",
		category: "issues",
		tier: "minor",
		status: "unlocked",
		icon: "CircleDot",
		requirement: "3 issues created",
		progress: 3,
		maxProgress: 3,
		level: 2,
		unlockedAt: "2024-01-25",
	},
	{
		id: "issue-5",
		name: "Issue Apprentice",
		description: "Create 5 issues",
		category: "issues",
		tier: "minor",
		status: "unlocked",
		icon: "CircleDot",
		requirement: "5 issues created",
		progress: 5,
		maxProgress: 5,
		level: 3,
		unlockedAt: "2024-02-08",
	},
	{
		id: "issue-10",
		name: "Bug Hunter",
		description: "Create 10 issues",
		category: "issues",
		tier: "notable",
		status: "unlocked",
		icon: "Bug",
		requirement: "10 issues created",
		progress: 10,
		maxProgress: 10,
		level: 4,
		unlockedAt: "2024-02-22",
	},
	{
		id: "issue-25",
		name: "Issue Specialist",
		description: "Create 25 issues",
		category: "issues",
		tier: "notable",
		status: "available",
		icon: "Target",
		requirement: "25 issues created",
		progress: 16,
		maxProgress: 25,
		level: 5,
	},
	{
		id: "issue-50",
		name: "Issue Commander",
		description: "Create 50 issues",
		category: "issues",
		tier: "keystone",
		status: "locked",
		icon: "Flag",
		requirement: "50 issues created",
		progress: 16,
		maxProgress: 50,
		level: 6,
	},
	{
		id: "issue-100",
		name: "The Tracker",
		description: "Create 100 issues - Nothing escapes your attention",
		category: "issues",
		tier: "legendary",
		status: "locked",
		icon: "Radar",
		requirement: "100 issues created",
		progress: 16,
		maxProgress: 100,
		level: 7,
	},
];

// COMMENTS LINE - Bottom-left branch (198 degrees)
const commentsLine: Achievement[] = [
	{
		id: "comment-1",
		name: "First Words",
		description: "Leave your first comment on a PR or issue",
		category: "comments",
		tier: "minor",
		status: "unlocked",
		icon: "MessageSquare",
		requirement: "1 comment",
		progress: 1,
		maxProgress: 1,
		level: 1,
		unlockedAt: "2024-01-16",
	},
	{
		id: "comment-5",
		name: "Conversationalist",
		description: "Leave 5 comments on PRs or issues",
		category: "comments",
		tier: "minor",
		status: "unlocked",
		icon: "MessageSquare",
		requirement: "5 comments",
		progress: 5,
		maxProgress: 5,
		level: 2,
		unlockedAt: "2024-01-20",
	},
	{
		id: "comment-10",
		name: "Comment Apprentice",
		description: "Leave 10 comments on PRs or issues",
		category: "comments",
		tier: "minor",
		status: "unlocked",
		icon: "MessageSquare",
		requirement: "10 comments",
		progress: 10,
		maxProgress: 10,
		level: 3,
		unlockedAt: "2024-01-28",
	},
	{
		id: "comment-25",
		name: "Active Discussant",
		description: "Leave 25 comments on PRs or issues",
		category: "comments",
		tier: "notable",
		status: "unlocked",
		icon: "MessagesSquare",
		requirement: "25 comments",
		progress: 25,
		maxProgress: 25,
		level: 4,
		unlockedAt: "2024-02-12",
	},
	{
		id: "comment-50",
		name: "Comment Specialist",
		description: "Leave 50 comments on PRs or issues",
		category: "comments",
		tier: "notable",
		status: "available",
		icon: "MessagesSquare",
		requirement: "50 comments",
		progress: 42,
		maxProgress: 50,
		level: 5,
	},
	{
		id: "comment-100",
		name: "Voice of the Team",
		description: "Leave 100 comments on PRs or issues",
		category: "comments",
		tier: "keystone",
		status: "locked",
		icon: "Megaphone",
		requirement: "100 comments",
		progress: 42,
		maxProgress: 100,
		level: 6,
	},
	{
		id: "comment-250",
		name: "The Communicator",
		description: "Leave 250 comments - The team's communication backbone",
		category: "comments",
		tier: "legendary",
		status: "locked",
		icon: "Radio",
		requirement: "250 comments",
		progress: 42,
		maxProgress: 250,
		level: 7,
	},
];

// ============================================
// CROSS-CATEGORY / INTERCONNECTING ACHIEVEMENTS
// ============================================

const crossCategoryAchievements: Achievement[] = [
	// PR + COMMITS connections
	{
		id: "cross-pr-commits-1",
		name: "Well Prepared",
		description: "Create a PR with 3+ commits",
		category: "crossCategory",
		tier: "minor",
		status: "unlocked",
		icon: "Layers",
		requirement: "PR with 3+ commits",
		progress: 1,
		maxProgress: 1,
		connects: ["pullRequests", "commits"],
		unlockedAt: "2024-01-28",
	},
	{
		id: "cross-pr-commits-2",
		name: "Thorough Work",
		description: "Create 5 PRs each with 5+ commits",
		category: "crossCategory",
		tier: "notable",
		status: "available",
		icon: "Layers",
		requirement: "5 PRs with 5+ commits each",
		progress: 3,
		maxProgress: 5,
		connects: ["pullRequests", "commits"],
	},

	// PR + REVIEWS connections
	{
		id: "cross-pr-review-1",
		name: "Feedback Welcome",
		description: "Receive feedback on your PR and address it",
		category: "crossCategory",
		tier: "minor",
		status: "unlocked",
		icon: "RotateCcw",
		requirement: "Address review feedback",
		progress: 1,
		maxProgress: 1,
		connects: ["pullRequests", "reviews"],
		unlockedAt: "2024-02-02",
	},
	{
		id: "cross-pr-review-2",
		name: "Both Sides",
		description: "Have 10 PRs merged AND submit 10 reviews",
		category: "crossCategory",
		tier: "notable",
		status: "unlocked",
		icon: "ArrowLeftRight",
		requirement: "10 PRs + 10 reviews",
		progress: 1,
		maxProgress: 1,
		connects: ["pullRequests", "reviews"],
		unlockedAt: "2024-02-18",
	},
	{
		id: "cross-pr-review-3",
		name: "Full Circle",
		description: "Have 25 PRs merged AND submit 25 reviews",
		category: "crossCategory",
		tier: "keystone",
		status: "locked",
		icon: "Circle",
		requirement: "25 PRs + 25 reviews",
		progress: 0,
		maxProgress: 1,
		connects: ["pullRequests", "reviews"],
	},

	// REVIEWS + COMMENTS connections
	{
		id: "cross-review-comment-1",
		name: "Detailed Reviewer",
		description: "Leave 3+ comments in a single review",
		category: "crossCategory",
		tier: "minor",
		status: "unlocked",
		icon: "ListChecks",
		requirement: "Review with 3+ comments",
		progress: 1,
		maxProgress: 1,
		connects: ["reviews", "comments"],
		unlockedAt: "2024-02-05",
	},
	{
		id: "cross-review-comment-2",
		name: "Constructive Critic",
		description: "Leave detailed reviews (3+ comments) on 10 PRs",
		category: "crossCategory",
		tier: "notable",
		status: "available",
		icon: "ScrollText",
		requirement: "10 detailed reviews",
		progress: 6,
		maxProgress: 10,
		connects: ["reviews", "comments"],
	},
	{
		id: "cross-review-comment-3",
		name: "Mentor",
		description: "Leave detailed reviews (3+ comments) on 25 PRs",
		category: "crossCategory",
		tier: "keystone",
		status: "locked",
		icon: "GraduationCap",
		requirement: "25 detailed reviews",
		progress: 6,
		maxProgress: 25,
		connects: ["reviews", "comments"],
	},

	// ISSUES + PR connections
	{
		id: "cross-issue-pr-1",
		name: "Problem Solver",
		description: "Close an issue with a PR you created",
		category: "crossCategory",
		tier: "minor",
		status: "unlocked",
		icon: "Wrench",
		requirement: "Close 1 issue via PR",
		progress: 1,
		maxProgress: 1,
		connects: ["issues", "pullRequests"],
		unlockedAt: "2024-02-08",
	},
	{
		id: "cross-issue-pr-2",
		name: "Bug Squasher",
		description: "Close 5 issues with PRs you created",
		category: "crossCategory",
		tier: "notable",
		status: "unlocked",
		icon: "Bug",
		requirement: "Close 5 issues via PRs",
		progress: 5,
		maxProgress: 5,
		connects: ["issues", "pullRequests"],
		unlockedAt: "2024-03-01",
	},
	{
		id: "cross-issue-pr-3",
		name: "Issue Annihilator",
		description: "Close 25 issues with PRs you created",
		category: "crossCategory",
		tier: "keystone",
		status: "locked",
		icon: "Flame",
		requirement: "Close 25 issues via PRs",
		progress: 8,
		maxProgress: 25,
		connects: ["issues", "pullRequests"],
	},

	// ISSUES + COMMENTS connections
	{
		id: "cross-issue-comment-1",
		name: "Issue Clarifier",
		description: "Add helpful comments to 5 different issues",
		category: "crossCategory",
		tier: "minor",
		status: "unlocked",
		icon: "HelpCircle",
		requirement: "Comment on 5 issues",
		progress: 5,
		maxProgress: 5,
		connects: ["issues", "comments"],
		unlockedAt: "2024-02-10",
	},
	{
		id: "cross-issue-comment-2",
		name: "Issue Expert",
		description: "Add helpful comments to 25 different issues",
		category: "crossCategory",
		tier: "notable",
		status: "available",
		icon: "Lightbulb",
		requirement: "Comment on 25 issues",
		progress: 14,
		maxProgress: 25,
		connects: ["issues", "comments"],
	},

	// COMMITS + COMMENTS connections
	{
		id: "cross-commit-comment-1",
		name: "Self Documenter",
		description: "Write 10 commits with detailed messages (50+ chars)",
		category: "crossCategory",
		tier: "minor",
		status: "unlocked",
		icon: "FileText",
		requirement: "10 detailed commit messages",
		progress: 10,
		maxProgress: 10,
		connects: ["commits", "comments"],
		unlockedAt: "2024-02-15",
	},

	// MULTI-CATEGORY MILESTONES
	{
		id: "cross-triple-1",
		name: "Triple Threat",
		description: "Have 5+ in three categories: commits, PRs, and reviews",
		category: "crossCategory",
		tier: "notable",
		status: "unlocked",
		icon: "Triangle",
		requirement: "5+ commits, PRs, reviews",
		progress: 1,
		maxProgress: 1,
		connects: ["commits", "pullRequests", "reviews"],
		unlockedAt: "2024-02-20",
	},
	{
		id: "cross-all-rounder-1",
		name: "All-Rounder",
		description: "Have 10+ in all five main categories",
		category: "crossCategory",
		tier: "keystone",
		status: "available",
		icon: "Pentagon",
		requirement: "10+ in all categories",
		progress: 4,
		maxProgress: 5,
		connects: ["commits", "pullRequests", "reviews", "issues", "comments"],
	},
	{
		id: "cross-all-rounder-2",
		name: "Renaissance Dev",
		description: "Have 25+ in all five main categories",
		category: "crossCategory",
		tier: "legendary",
		status: "locked",
		icon: "Star",
		requirement: "25+ in all categories",
		progress: 2,
		maxProgress: 5,
		connects: ["commits", "pullRequests", "reviews", "issues", "comments"],
	},

	// SECRET/HIDDEN ACHIEVEMENTS
	{
		id: "secret-first-day",
		name: "Day One Hero",
		description: "Make a commit, open a PR, and leave a review all in one day",
		category: "crossCategory",
		tier: "notable",
		status: "unlocked",
		icon: "Sparkles",
		requirement: "All three in one day",
		progress: 1,
		maxProgress: 1,
		connects: ["commits", "pullRequests", "reviews"],
		hidden: true,
		unlockedAt: "2024-01-15",
	},
	{
		id: "secret-helpful",
		name: "Helping Hand",
		description: "Help 3 different team members by reviewing their PRs",
		category: "crossCategory",
		tier: "minor",
		status: "unlocked",
		icon: "HandHelping",
		requirement: "Review 3 teammates' PRs",
		progress: 3,
		maxProgress: 3,
		connects: ["reviews"],
		hidden: true,
		unlockedAt: "2024-02-25",
	},
	{
		id: "secret-quick-fix",
		name: "Quick Fix",
		description: "Create and merge a PR within 1 hour",
		category: "crossCategory",
		tier: "notable",
		status: "locked",
		icon: "Timer",
		requirement: "1-hour PR turnaround",
		progress: 0,
		maxProgress: 1,
		connects: ["pullRequests"],
		hidden: true,
	},
	{
		id: "secret-marathon",
		name: "Marathon Session",
		description: "Make 10+ commits in a single day",
		category: "crossCategory",
		tier: "notable",
		status: "locked",
		icon: "Flame",
		requirement: "10 commits in one day",
		progress: 0,
		maxProgress: 1,
		connects: ["commits"],
		hidden: true,
	},
];

// Combine all achievements
export const achievements: Achievement[] = [
	...commitsLine,
	...pullRequestsLine,
	...reviewsLine,
	...issuesLine,
	...commentsLine,
	...crossCategoryAchievements,
];

// Explicit positions for cross-category achievements
// Main branches: Commits(270 deg), PRs(342 deg), Reviews(54 deg), Issues(126 deg), Comments(198 deg)
// Cross achievements positioned in arcs BETWEEN the main branches
const crossAchievementPositions: Record<string, { angle: number; distance: number }> = {
	// === ARC 1: COMMITS <-> PRS (between 270 deg and 342 deg, midpoint ~306 deg) ===
	"cross-pr-commits-1": { angle: 306, distance: 200 },
	"cross-pr-commits-2": { angle: 306, distance: 400 },

	// === ARC 2: PRS <-> REVIEWS (between 342 deg and 54 deg, midpoint ~18 deg) ===
	"cross-pr-review-1": { angle: 18, distance: 200 },
	"cross-pr-review-2": { angle: 18, distance: 380 },
	"cross-pr-review-3": { angle: 18, distance: 560 },

	// === ARC 3: REVIEWS <-> ISSUES (between 54 deg and 126 deg, midpoint ~90 deg) ===
	"cross-review-comment-1": { angle: 90, distance: 200 },
	"cross-review-comment-2": { angle: 90, distance: 380 },
	"cross-review-comment-3": { angle: 90, distance: 560 },

	// === ARC 4: ISSUES <-> COMMENTS (between 126 deg and 198 deg, midpoint ~162 deg) ===
	"cross-issue-pr-1": { angle: 162, distance: 220 },
	"cross-issue-pr-2": { angle: 162, distance: 400 },
	"cross-issue-pr-3": { angle: 162, distance: 580 },
	"cross-issue-comment-1": { angle: 148, distance: 280 },
	"cross-issue-comment-2": { angle: 176, distance: 420 },

	// === ARC 5: COMMENTS <-> COMMITS (between 198 deg and 270 deg, midpoint ~234 deg) ===
	"cross-commit-comment-1": { angle: 234, distance: 280 },

	// === CENTER MILESTONES (inner ring, evenly distributed) ===
	"cross-triple-1": { angle: 0, distance: 350 },
	"cross-all-rounder-1": { angle: 0, distance: 500 },
	"cross-all-rounder-2": { angle: 0, distance: 680 },

	// === SECRET/HIDDEN (tucked in various arcs) ===
	"secret-first-day": { angle: 306, distance: 300 },
	"secret-helpful": { angle: 36, distance: 300 },
	"secret-quick-fix": { angle: 324, distance: 500 },
	"secret-marathon": { angle: 252, distance: 340 },
};

// Generate nodes and edges for React Flow
export function generateSkillTreeData(user?: {
	name?: string;
	avatarUrl?: string;
	level?: number;
	leaguePoints?: number;
}): {
	nodes: Node<AchievementNodeData>[];
	edges: Edge[];
} {
	const nodes: Node<AchievementNodeData>[] = [];
	const edges: Edge[] = [];
	const centerX = 0;
	const centerY = 0;

	// Add Central Avatar Node
	nodes.push({
		id: "root-avatar",
		type: "avatar",
		position: { x: centerX, y: centerY },
		data: {
			id: "root-avatar",
			name: user?.name || "User",
			description: "You",
			category: "crossCategory",
			tier: "legendary",
			status: "unlocked",
			icon: "User",
			requirement: "",
			angle: 0,
			ring: 0,
			// Mock data for avatar appearance
			level: user?.level ?? 42,
			leaguePoints: user?.leaguePoints ?? 1600, // Gold tier
			avatarUrl: user?.avatarUrl,
		},
		zIndex: 10,
	});

	// Ring distances for main lines - progressive achievements spread outward
	const levelDistances: Record<number, number> = {
		1: 180, // Increased slightly to make room for avatar
		2: 270,
		3: 360,
		4: 460,
		5: 560,
		6: 670,
		7: 800,
	};

	// Process main line achievements (non-cross-category)
	const mainCategories: AchievementCategory[] = [
		"commits",
		"pullRequests",
		"reviews",
		"issues",
		"comments",
	];

	for (const category of mainCategories) {
		const categoryAchievements = achievements.filter((a) => a.category === category);
		const baseAngle = categoryMeta[category].angle;

		categoryAchievements.forEach((achievement, idx) => {
			const level = achievement.level || idx + 1;
			const distance = levelDistances[level] || 400;

			const radians = (baseAngle * Math.PI) / 180;
			const x = centerX + distance * Math.cos(radians);
			const y = centerY + distance * Math.sin(radians);

			nodes.push({
				id: achievement.id,
				type: "achievement",
				position: { x, y },
				data: {
					...achievement,
					angle: baseAngle,
					ring: level,
				},
			});

			// Connect to previous achievement in the line
			if (idx > 0) {
				const prevAchievement = categoryAchievements[idx - 1];
				edges.push({
					id: `${prevAchievement.id}-${achievement.id}`,
					source: prevAchievement.id,
					target: achievement.id,
					type: "skill",
					data: {
						active: prevAchievement.status === "unlocked" && achievement.status !== "locked",
					},
				});
			} else {
				// Connect to Root Avatar for the first item
				edges.push({
					id: `root-${achievement.id}`,
					source: "root-avatar",
					target: achievement.id,
					type: "skill",
					data: {
						active: achievement.status !== "locked",
					},
				});
			}
		});
	}

	// Process cross-category achievements with explicit positions
	const crossAchievements = achievements.filter((a) => a.category === "crossCategory");

	for (const achievement of crossAchievements) {
		const position = crossAchievementPositions[achievement.id];
		if (!position) continue;

		const radians = (position.angle * Math.PI) / 180;
		const x = centerX + position.distance * Math.cos(radians);
		const y = centerY + position.distance * Math.sin(radians);

		nodes.push({
			id: achievement.id,
			type: "achievement",
			position: { x, y },
			data: {
				...achievement,
				angle: position.angle,
				ring: Math.floor(position.distance / 150),
			},
		});
	}

	// === CROSS-CATEGORY EDGE CONNECTIONS ===
	// Each arc connects adjacent main branches with bridge achievements

	// ARC 1: COMMITS <-> PRS (306 deg arc)
	addCrossEdge(edges, "commit-5", "cross-pr-commits-1");
	addCrossEdge(edges, "pr-3", "cross-pr-commits-1");
	addCrossEdge(edges, "cross-pr-commits-1", "cross-pr-commits-2");

	// ARC 2: PRS <-> REVIEWS (18 deg arc)
	addCrossEdge(edges, "pr-3", "cross-pr-review-1");
	addCrossEdge(edges, "review-3", "cross-pr-review-1");
	addCrossEdge(edges, "cross-pr-review-1", "cross-pr-review-2");
	addCrossEdge(edges, "cross-pr-review-2", "cross-pr-review-3");

	// ARC 3: REVIEWS <-> ISSUES (90 deg arc) - reusing review-comment achievements here
	addCrossEdge(edges, "review-5", "cross-review-comment-1");
	addCrossEdge(edges, "cross-review-comment-1", "cross-review-comment-2");
	addCrossEdge(edges, "cross-review-comment-2", "cross-review-comment-3");

	// ARC 4: ISSUES <-> COMMENTS (162 deg arc)
	addCrossEdge(edges, "issue-5", "cross-issue-pr-1");
	addCrossEdge(edges, "cross-issue-pr-1", "cross-issue-pr-2");
	addCrossEdge(edges, "cross-issue-pr-2", "cross-issue-pr-3");
	addCrossEdge(edges, "issue-3", "cross-issue-comment-1");
	addCrossEdge(edges, "comment-10", "cross-issue-comment-1");
	addCrossEdge(edges, "cross-issue-comment-1", "cross-issue-comment-2");

	// ARC 5: COMMENTS <-> COMMITS (234 deg arc)
	addCrossEdge(edges, "comment-10", "cross-commit-comment-1");
	addCrossEdge(edges, "commit-10", "cross-commit-comment-1");

	// CENTER MILESTONES - connect from inner cross achievements
	addCrossEdge(edges, "cross-pr-review-2", "cross-triple-1");
	addCrossEdge(edges, "cross-triple-1", "cross-all-rounder-1");
	addCrossEdge(edges, "cross-all-rounder-1", "cross-all-rounder-2");

	// SECRET achievements - connect to nearby achievements
	addCrossEdge(edges, "cross-pr-commits-1", "secret-first-day");
	addCrossEdge(edges, "cross-pr-review-1", "secret-helpful");
	addCrossEdge(edges, "cross-pr-commits-2", "secret-quick-fix");
	addCrossEdge(edges, "cross-commit-comment-1", "secret-marathon");

	return { nodes, edges };
}

// Helper to create cross-category edges
function addCrossEdge(edges: Edge[], sourceId: string, targetId: string) {
	const sourceAchievement = achievements.find((a) => a.id === sourceId);
	const targetAchievement = achievements.find((a) => a.id === targetId);

	if (sourceAchievement && targetAchievement) {
		edges.push({
			id: `${sourceId}-${targetId}`,
			source: sourceId,
			target: targetId,
			type: "skill",
			data: {
				active: sourceAchievement.status === "unlocked" && targetAchievement.status !== "locked",
			},
		});
	}
}

// Stats calculation
export function calculateStats(achievementList: Achievement[]) {
	const total = achievementList.length;
	const unlocked = achievementList.filter((a) => a.status === "unlocked").length;
	const available = achievementList.filter((a) => a.status === "available").length;

	const mainCategories: AchievementCategory[] = [
		"commits",
		"pullRequests",
		"reviews",
		"issues",
		"comments",
	];

	const byCategory = mainCategories.reduce(
		(acc, cat) => {
			const catAchievements = achievementList.filter((a) => a.category === cat);
			acc[cat] = {
				total: catAchievements.length,
				unlocked: catAchievements.filter((a) => a.status === "unlocked").length,
			};
			return acc;
		},
		{} as Record<AchievementCategory, { total: number; unlocked: number }>,
	);

	// Add cross-category stats
	const crossAchievements = achievementList.filter((a) => a.category === "crossCategory");
	byCategory.crossCategory = {
		total: crossAchievements.length,
		unlocked: crossAchievements.filter((a) => a.status === "unlocked").length,
	};

	return {
		total,
		unlocked,
		available,
		percentage: Math.round((unlocked / total) * 100),
		byCategory,
	};
}
