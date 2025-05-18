import type { LeaderboardEntry } from "@/api/types.gen";
import type { Meta, StoryObj } from "@storybook/react";
import { LeaderboardTable } from "./LeaderboardTable";

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
		reviewedPullRequests: [],
		numberOfReviewedPRs: 18,
		numberOfApprovals: 8,
		numberOfChangeRequests: 7,
		numberOfComments: 2,
		numberOfUnknowns: 1,
		numberOfCodeComments: 5,
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
		reviewedPullRequests: [],
		numberOfReviewedPRs: 8,
		numberOfApprovals: 1,
		numberOfChangeRequests: 5,
		numberOfComments: 2,
		numberOfUnknowns: 0,
		numberOfCodeComments: 21,
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
		reviewedPullRequests: [],
		numberOfReviewedPRs: 5,
		numberOfApprovals: 3,
		numberOfChangeRequests: 1,
		numberOfComments: 0,
		numberOfUnknowns: 0,
		numberOfCodeComments: 2,
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
		reviewedPullRequests: [],
		numberOfReviewedPRs: 3,
		numberOfApprovals: 0,
		numberOfChangeRequests: 1,
		numberOfComments: 1,
		numberOfUnknowns: 1,
		numberOfCodeComments: 5,
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
	},
];

const meta: Meta<typeof LeaderboardTable> = {
	component: LeaderboardTable,
	tags: ["autodocs"],
	parameters: {
		layout: "padded",
	},
};

export default meta;
type Story = StoryObj<typeof LeaderboardTable>;

export const Default: Story = {
	args: {
		leaderboard: mockLeaderboardEntries,
		isLoading: false,
	},
};

export const Loading: Story = {
	args: {
		leaderboard: [],
		isLoading: true,
	},
};

export const Empty: Story = {
	args: {
		leaderboard: [],
		isLoading: false,
	},
};

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
