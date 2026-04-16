import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import type { LeaderboardEntry } from "@/api/types.gen";
import { LeaderboardTable } from "./LeaderboardTable";

const reviewedPullRequests = [
	{
		id: 201,
		number: 42,
		title: "Improve leaderboard semantics",
		state: "OPEN" as const,
		isDraft: false,
		isMerged: false,
		commentsCount: 2,
		additions: 35,
		deletions: 5,
		htmlUrl: "https://github.com/ls1intum/Hephaestus/pull/42",
		repository: {
			id: 1,
			name: "Hephaestus",
			nameWithOwner: "ls1intum/Hephaestus",
			htmlUrl: "https://github.com/ls1intum/Hephaestus",
			hiddenFromContributions: false,
		},
	},
	{
		id: 202,
		number: 314,
		title: "Refine profile activity feed",
		state: "OPEN" as const,
		isDraft: false,
		isMerged: false,
		commentsCount: 4,
		additions: 120,
		deletions: 15,
		htmlUrl: "https://github.com/ls1intum/Artemis/pull/314",
		repository: {
			id: 2,
			name: "Artemis",
			nameWithOwner: "ls1intum/Artemis",
			htmlUrl: "https://github.com/ls1intum/Artemis",
			hiddenFromContributions: false,
		},
	},
];

/**
 * A table component that displays leaderboard data with user rankings, scores, and activity metrics.
 */

const mockLeaderboardEntries: LeaderboardEntry[] = [
	{
		rank: 1,
		score: 100,
		user: {
			id: 0,
			login: "GODrums",
			avatarUrl: "https://avatars.githubusercontent.com/u/21990230?v=4",
			name: "Armin Stanitzok",
			htmlUrl: "https://github.com/GODrums",
			leaguePoints: 1250,
		},
		reviewedPullRequests,
		numberOfReviewedPRs: 18,
		numberOfApprovals: 8,
		numberOfChangeRequests: 7,
		numberOfComments: 2,
		numberOfUnknowns: 1,
		numberOfCodeComments: 5,
		numberOfOwnReplies: 3,
		numberOfOpenPullRequests: 2,
		numberOfMergedPullRequests: 6,
		numberOfClosedPullRequests: 1,
		numberOfOpenedIssues: 4,
		numberOfClosedIssues: 2,
	},
	{
		rank: 2,
		score: 90,
		user: {
			id: 1,
			login: "FelixTJDietrich",
			avatarUrl: "https://avatars.githubusercontent.com/u/5898705?v=4",
			name: "Felix T.J. Dietrich",
			htmlUrl: "https://github.com/FelixTJDietrich",
			leaguePoints: 750,
		},
		reviewedPullRequests: reviewedPullRequests.slice(0, 1),
		numberOfReviewedPRs: 8,
		numberOfApprovals: 1,
		numberOfChangeRequests: 5,
		numberOfComments: 2,
		numberOfUnknowns: 0,
		numberOfCodeComments: 21,
		numberOfOwnReplies: 5,
		numberOfOpenPullRequests: 1,
		numberOfMergedPullRequests: 3,
		numberOfClosedPullRequests: 0,
		numberOfOpenedIssues: 2,
		numberOfClosedIssues: 1,
	},
	{
		rank: 3,
		score: 50,
		user: {
			id: 2,
			login: "krusche",
			avatarUrl: "https://avatars.githubusercontent.com/u/744067?v=4",
			name: "Stephan Krusche",
			htmlUrl: "https://github.com/krusche",
			leaguePoints: 2500,
		},
		reviewedPullRequests: reviewedPullRequests.slice(0, 1),
		numberOfReviewedPRs: 5,
		numberOfApprovals: 3,
		numberOfChangeRequests: 1,
		numberOfComments: 0,
		numberOfUnknowns: 0,
		numberOfCodeComments: 2,
		numberOfOwnReplies: 0,
		numberOfOpenPullRequests: 0,
		numberOfMergedPullRequests: 2,
		numberOfClosedPullRequests: 0,
		numberOfOpenedIssues: 1,
		numberOfClosedIssues: 1,
	},
	{
		rank: 4,
		score: 20,
		user: {
			id: 3,
			login: "shadcn",
			avatarUrl: "https://avatars.githubusercontent.com/u/124599?v=4",
			name: "shadcn",
			htmlUrl: "https://github.com/shadcn",
			leaguePoints: 350,
		},
		reviewedPullRequests: reviewedPullRequests.slice(0, 1),
		numberOfReviewedPRs: 3,
		numberOfApprovals: 0,
		numberOfChangeRequests: 1,
		numberOfComments: 1,
		numberOfUnknowns: 1,
		numberOfCodeComments: 5,
		numberOfOwnReplies: 2,
		numberOfOpenPullRequests: 1,
		numberOfMergedPullRequests: 0,
		numberOfClosedPullRequests: 2,
		numberOfOpenedIssues: 0,
		numberOfClosedIssues: 3,
	},
	{
		rank: 5,
		score: 0,
		user: {
			id: 3,
			login: "doesnotexistongithub",
			avatarUrl: "https://avatars.githubusercontentd.com/u/13132323124599?v=4",
			name: "NoAvatarUser",
			htmlUrl: "https://github.com/NoAvatarUser",
			leaguePoints: 100,
		},
		reviewedPullRequests: [],
		numberOfReviewedPRs: 0,
		numberOfApprovals: 0,
		numberOfChangeRequests: 0,
		numberOfComments: 0,
		numberOfUnknowns: 0,
		numberOfCodeComments: 0,
		numberOfOwnReplies: 0,
		numberOfOpenPullRequests: 0,
		numberOfMergedPullRequests: 0,
		numberOfClosedPullRequests: 0,
		numberOfOpenedIssues: 0,
		numberOfClosedIssues: 0,
	},
];

const meta = {
	component: LeaderboardTable,
	tags: ["autodocs"],
	parameters: {
		layout: "padded",
	},
	argTypes: {
		leaderboard: {
			description: "Array of leaderboard entries to display",
		},
		isLoading: {
			description: "Whether the leaderboard data is currently loading",
			control: "boolean",
		},
		currentUser: {
			description: "Currently logged in user info to highlight their row",
		},
		onUserClick: {
			description: "Callback function when a user row is clicked",
			action: "clicked",
		},
	},
	args: {
		onUserClick: fn(),
		variant: "INDIVIDUAL",
	},
} satisfies Meta<typeof LeaderboardTable>;

export default meta;
type Story = StoryObj<typeof LeaderboardTable>;

/**
 * Default display of the leaderboard with entries and navigation callback.
 */
export const Default: Story = {
	args: {
		leaderboard: mockLeaderboardEntries,
		isLoading: false,
	},
};

/**
 * Loading state showing skeleton placeholders.
 */
export const Loading: Story = {
	args: {
		leaderboard: [],
		isLoading: true,
	},
};

/**
 * Empty state shown when no leaderboard entries are available.
 */
export const Empty: Story = {
	args: {
		leaderboard: [],
		isLoading: false,
	},
};

/**
 * Shows the leaderboard with the current user highlighted.
 */
export const WithCurrentUser: Story = {
	args: {
		leaderboard: mockLeaderboardEntries,
		isLoading: false,
		currentUser: {
			id: 1,
			login: "FelixTJDietrich",
			name: "Felix T.J. Dietrich",
			avatarUrl: "https://avatars.githubusercontent.com/u/5898705?v=4",
			htmlUrl: "https://github.com/FelixTJDietrich",
		},
	},
};
