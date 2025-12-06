import type { Meta, StoryObj } from "@storybook/react";
import type { LeaderboardEntry } from "@/api/types.gen";
import { ActivityBadges } from "./ActivityBadges";

const basePRs: LeaderboardEntry["reviewedPullRequests"] = [
	{
		id: 1,
		number: 42,
		title: "Improve docs",
		htmlUrl: "https://github.com/example/repo/pull/42",
		additions: 10,
		deletions: 2,
		commentsCount: 0,
		isDraft: false,
		isMerged: false,
		createdAt: new Date(),
		state: "OPEN",
		labels: [],
		repository: {
			id: 100,
			name: "repo",
			nameWithOwner: "example/repo",
			htmlUrl: "https://github.com/example/repo",
			hiddenFromContributions: false,
		},
	},
	{
		id: 2,
		number: 7,
		title: "Fix tests",
		htmlUrl: "https://github.com/example/repo/pull/7",
		additions: 5,
		deletions: 1,
		commentsCount: 0,
		isDraft: false,
		isMerged: false,
		createdAt: new Date(),
		state: "OPEN",
		labels: [],
		repository: {
			id: 101,
			name: "repo",
			nameWithOwner: "example/repo",
			htmlUrl: "https://github.com/example/repo",
			hiddenFromContributions: false,
		},
	},
];

const meta = {
	title: "Leaderboard/ActivityBadges",
	component: ActivityBadges,
	args: {
		reviewedPullRequests: basePRs,
		approvals: 3,
		changeRequests: 1,
		comments: 5,
		codeComments: 2,
	},
} satisfies Meta<typeof ActivityBadges>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const ReviewsOnly: Story = {
	args: {
		reviewedPullRequests: basePRs,
		approvals: 0,
		changeRequests: 0,
		comments: 0,
		codeComments: 0,
		highlightReviews: true,
	},
};

export const Minimal: Story = {
	args: {
		reviewedPullRequests: [],
		approvals: 0,
		changeRequests: 1,
		comments: 0,
		codeComments: 0,
	},
};
