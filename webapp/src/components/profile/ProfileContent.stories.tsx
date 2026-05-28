import type { Meta, StoryObj } from "@storybook/react";
import { endOfISOWeek, formatISO, startOfISOWeek, subDays } from "date-fns";
import { fn } from "storybook/test";
import { ProfileContent } from "./ProfileContent";

const now = new Date();
const defaultAfter = formatISO(startOfISOWeek(now));
const defaultBefore = formatISO(endOfISOWeek(now));

const repositories = [
	{
		id: 1,
		name: "Hephaestus",
		nameWithOwner: "ls1intum/Hephaestus",
		htmlUrl: "https://github.com/ls1intum/Hephaestus",
		hiddenFromContributions: false,
	},
	{
		id: 2,
		name: "Artemis",
		nameWithOwner: "ls1intum/Artemis",
		htmlUrl: "https://github.com/ls1intum/Artemis",
		hiddenFromContributions: false,
	},
	{
		id: 3,
		name: "Athena",
		nameWithOwner: "ls1intum/Athena",
		htmlUrl: "https://github.com/ls1intum/Athena",
		hiddenFromContributions: false,
	},
];

const reviewActivity = [
	{
		id: 1,
		state: "APPROVED" as const,
		submittedAt: subDays(new Date(), 2),
		htmlUrl: "https://github.com/ls1intum/Hephaestus/pull/42",
		pullRequest: {
			id: 101,
			title: "Add new feature to dashboard",
			number: 42,
			state: "OPEN" as const,
			isDraft: false,
			isMerged: false,
			htmlUrl: "https://github.com/ls1intum/Hephaestus/pull/42",
			repository: repositories[0],
		},
		score: 80,
		isDismissed: false,
		codeComments: 3,
	},
	{
		id: 2,
		state: "CHANGES_REQUESTED" as const,
		submittedAt: subDays(new Date(), 5),
		htmlUrl: "https://github.com/ls1intum/Artemis/pull/123",
		pullRequest: {
			id: 102,
			title: "Fix authentication bugs",
			number: 123,
			state: "OPEN" as const,
			isDraft: false,
			isMerged: false,
			htmlUrl: "https://github.com/ls1intum/Artemis/pull/123",
			repository: repositories[1],
		},
		score: 65,
		isDismissed: false,
		codeComments: 2,
	},
	{
		id: 3,
		state: "COMMENTED" as const,
		submittedAt: subDays(new Date(), 7),
		htmlUrl: "https://github.com/ls1intum/Athena/pull/15",
		pullRequest: {
			id: 103,
			title: "Update documentation",
			number: 15,
			state: "OPEN" as const,
			isDraft: false,
			isMerged: false,
			htmlUrl: "https://github.com/ls1intum/Athena/pull/15",
			repository: repositories[2],
		},
		score: 50,
		isDismissed: false,
		codeComments: 0,
	},
];

const authoredPullRequests = [
	{
		id: 101,
		number: 42,
		title: "Add new analytics dashboard",
		state: "OPEN" as const,
		isDraft: false,
		isMerged: false,
		commentsCount: 5,
		additions: 250,
		deletions: 30,
		htmlUrl: "https://github.com/ls1intum/Hephaestus/pull/42",
		createdAt: subDays(new Date(), 3),
		repository: repositories[0],
		labels: [
			{ id: 1, name: "enhancement", color: "0E8A16" },
			{ id: 2, name: "frontend", color: "FBCA04" },
		],
	},
	{
		id: 102,
		number: 87,
		title: "WIP: Refactor authentication module",
		state: "OPEN" as const,
		isDraft: true,
		isMerged: false,
		commentsCount: 0,
		additions: 320,
		deletions: 280,
		htmlUrl: "https://github.com/ls1intum/Artemis/pull/87",
		createdAt: subDays(new Date(), 1),
		repository: repositories[1],
		labels: [
			{ id: 3, name: "refactoring", color: "D93F0B" },
			{ id: 4, name: "security", color: "5319E7" },
		],
	},
];

const activityStats = {
	score: 195,
	numberOfReviewedPRs: 3,
	numberOfApprovals: 1,
	numberOfChangeRequests: 1,
	numberOfComments: 1,
	numberOfCodeComments: 5,
	numberOfUnknowns: 0,
	numberOfOwnReplies: 2,
	numberOfOpenPullRequests: 2,
	numberOfMergedPullRequests: 1,
	numberOfClosedPullRequests: 0,
	numberOfOpenedIssues: 1,
	numberOfClosedIssues: 1,
};

const filledMonitor = {
	activityStats,
	reviewActivity,
	authoredPullRequests,
	repositories,
	totalReviewActivityCount: 8,
	totalAuthoredPullRequestCount: 6,
};

const emptyMonitor = {
	activityStats: {
		score: 0,
		numberOfReviewedPRs: 0,
		numberOfApprovals: 0,
		numberOfChangeRequests: 0,
		numberOfComments: 0,
		numberOfCodeComments: 0,
		numberOfUnknowns: 0,
		numberOfOwnReplies: 0,
		numberOfOpenPullRequests: 0,
		numberOfMergedPullRequests: 0,
		numberOfClosedPullRequests: 0,
		numberOfOpenedIssues: 0,
		numberOfClosedIssues: 0,
	},
	reviewActivity: [],
	authoredPullRequests: [],
	repositories: [],
	totalReviewActivityCount: 0,
	totalAuthoredPullRequestCount: 0,
};

const baseArgs = {
	isLoading: false,
	username: "johndoe",
	currUserIsDashboardUser: true,
	workspaceSlug: "aet",
	afterDate: defaultAfter,
	beforeDate: defaultBefore,
	activityMonitorFilters: { repositoryIds: [], limit: 5 },
	onActivityMonitorFiltersChange: fn(),
	onTimeframeChange: fn(),
};

const meta = {
	component: ProfileContent,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof ProfileContent>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	args: { ...baseArgs, activityMonitorData: filledMonitor },
};

export const RepositoryFiltered: Story = {
	args: {
		...baseArgs,
		activityMonitorFilters: { repositoryIds: [1], limit: 5 },
		activityMonitorData: {
			...filledMonitor,
			reviewActivity: reviewActivity.slice(0, 1),
			authoredPullRequests: authoredPullRequests.slice(0, 1),
			totalReviewActivityCount: 1,
			totalAuthoredPullRequestCount: 1,
		},
	},
};

export const Loading: Story = {
	args: { ...baseArgs, isLoading: true },
};

export const EmptyReviews: Story = {
	args: {
		...baseArgs,
		activityMonitorData: {
			...filledMonitor,
			reviewActivity: [],
			totalReviewActivityCount: 0,
			activityStats: { ...activityStats, numberOfReviewedPRs: 0, numberOfApprovals: 0 },
		},
	},
};

export const EmptyPullRequests: Story = {
	args: {
		...baseArgs,
		activityMonitorData: {
			...filledMonitor,
			authoredPullRequests: [],
			totalAuthoredPullRequestCount: 0,
			activityStats: { ...activityStats, numberOfOpenPullRequests: 0 },
		},
	},
};

export const CompletelyEmpty: Story = {
	args: { ...baseArgs, activityMonitorData: emptyMonitor },
};
