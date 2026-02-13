import { CodeReviewIcon } from "@primer/octicons-react";
import { defaultCategoryIcons } from "@/components/achievements/styles.ts";
import type { AchievementDisplay, AchievementId } from "@/components/achievements/types.ts";

// type PartialAchievementRegistry = Partial<Record<AchievementId, Partial<AchievementDisplay>>>;
type PartialAchievementRegistry = Partial<Record<AchievementId, AchievementDisplay>>;

const pullRequestAchievement = {
	first_pull: {
		name: "First Merge",
		description: "Merge your first Pull Request",
		icon: defaultCategoryIcons.pull_requests,
	},
	pr_beginner: {
		name: "Beginner Integrator",
		description: "Merge 3 Pull Requests",
		icon: defaultCategoryIcons.pull_requests,
	},
	pr_apprentice: {
		name: "Apprentice Integrator",
		description: "Merge 5 Pull Requests",
		icon: defaultCategoryIcons.pull_requests,
	},
	integration_regular: {
		name: "Integration Regular",
		description: "Merge 10 Pull Requests",
		icon: defaultCategoryIcons.pull_requests,
	},
	pr_specialist: {
		name: "Integration Specialist",
		description: "Merge 25 Pull Requests",
		icon: defaultCategoryIcons.pull_requests,
	},
	integration_expert: {
		name: "Integration Expert",
		description: "Merge 50 Pull Requests",
		icon: defaultCategoryIcons.pull_requests,
	},
	master_integrator: {
		name: "Master Integrator",
		description: "Merge 100 Pull Requests",
		icon: defaultCategoryIcons.pull_requests,
	},
} satisfies PartialAchievementRegistry;

const communicationAchievements = {
	first_review: {
		name: "First Review",
		description: "Submit your first code review",
		icon: defaultCategoryIcons.communication,
	},
	review_rookie: {
		name: "Review Rookie",
		description: "Submit 10 code reviews",
		icon: defaultCategoryIcons.communication,
	},
	review_master: {
		name: "Review Master",
		description: "Submit 100 code reviews",
		icon: defaultCategoryIcons.communication,
	},
	code_commenter: {
		name: "Code Commenter",
		description: "Post 100 code comments",
		icon: defaultCategoryIcons.communication,
	},
	helpful_reviewer: {
		name: "Helpful Reviewer",
		description: "Approve 50 pull requests",
		icon: CodeReviewIcon,
	},
} satisfies PartialAchievementRegistry;

export const ACHIEVEMENT_REGISTRY = {
	...pullRequestAchievement,
	...communicationAchievements,
} as const satisfies Record<AchievementId, AchievementDisplay>;
