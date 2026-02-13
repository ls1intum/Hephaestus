import {
	CommentIcon,
	GitCommitIcon,
	GitPullRequestIcon,
	IssueOpenedIcon,
} from "@primer/octicons-react";
import { MilestoneIcon } from "lucide-react";
import type React from "react";
import type { AchievementCategory, AchievementRarity } from "@/components/achievements/types";

export const tierSizes = {
	common: "w-10 h-10",
	uncommon: "w-11 h-11",
	rare: "w-12 h-12",
	epic: "w-14 h-14",
	legendary: "w-16 h-16",
	mythic: "w-20 h-20",
} as const satisfies Record<AchievementRarity, string>;

export const tierIconSizes = {
	common: 14,
	uncommon: 16,
	rare: 18,
	epic: 22,
	legendary: 26,
	mythic: 32,
} as const satisfies Record<AchievementRarity, number>;

export const categoryLabels = {
	pull_requests: "Pull Requests",
	commits: "Commits",
	communication: "Communication",
	issues: "Issues",
	milestones: "Milestones",
} as const satisfies Record<AchievementCategory, string>;

export const defaultCategoryIcons = {
	pull_requests: GitPullRequestIcon,
	commits: GitCommitIcon,
	communication: CommentIcon,
	issues: IssueOpenedIcon,
	milestones: MilestoneIcon,
} as const satisfies Record<AchievementCategory, React.ElementType>;
