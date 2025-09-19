import type { Meta, StoryObj } from "@storybook/react";
import type { LabelInfo, RepositoryInfo, TeamInfo, TeamLeaderboardEntry } from "@/api/types.gen.ts";
import { TeamLeaderboardPage } from "./TeamLeaderboardPage.tsx";

const mockTeams = ["Frontend", "Backend", "DevOps", "QA", "Design"];

const mockRepositories: RepositoryInfo[] = [
	{
		id: 1,
		name: "webapp",
		nameWithOwner: "team/webapp",
		description: "Frontend repo",
		htmlUrl: "https://github.com/team/webapp",
	},
	{
		id: 2,
		name: "api",
		nameWithOwner: "team/api",
		description: "Backend repo",
		htmlUrl: "https://github.com/team/api",
	},
];

const mockLabels: LabelInfo[] = [
	{ id: 1, name: "bug", color: "#d73a4a" },
	{ id: 2, name: "feature", color: "#a2eeef" },
];

const mockTeamInfos: TeamInfo[] = [
	{
		id: 1,
		name: "Frontend",
		description: "Frontend team responsible for UI/UX.",
		hidden: false,
		labels: mockLabels,
		members: [
			{
				id: 101,
				login: "alice",
				name: "Alice Developer",
				avatarUrl: "/assets/alice_developer.jpg",
				htmlUrl: "https://github.com/alice",
				leaguePoints: 1200,
			},
		],
		membershipCount: 1,
		repoPermissionCount: 0,
		repositories: [],
	},
	{
		id: 2,
		name: "Backend",
		description: "Backend team responsible for API and server logic.",
		hidden: false,
		labels: mockLabels,
		members: [
			{
				id: 102,
				login: "bob",
				name: "Bob Builder",
				avatarUrl: "/assets/bob_builder.jpg",
				htmlUrl: "https://github.com/bob",
				leaguePoints: 950,
			},
		],
		membershipCount: 1,
		repoPermissionCount: 1,
		repositories: [mockRepositories[1]],
	},
	{
		id: 3,
		name: "QA",
		description: "QA team responsible for testing and quality assurance.",
		hidden: false,
		labels: [],
		members: [
			{
				id: 103,
				login: "charlie",
				name: "Charlie Coder",
				avatarUrl: "/assets/charlie_coder.jpg",
				htmlUrl: "https://github.com/charlie",
				leaguePoints: 800,
			},
		],
		membershipCount: 1,
		repoPermissionCount: 0,
		repositories: [],
	},
];

const mockTeamLeaderboard: TeamLeaderboardEntry[] = [
	{
		rank: 1,
		score: 250,
		team: mockTeamInfos[0],
		reviewedPullRequests: [],
		numberOfReviewedPRs: 20,
		numberOfApprovals: 10,
		numberOfChangeRequests: 5,
		numberOfComments: 8,
		numberOfUnknowns: 2,
		numberOfCodeComments: 15,
	},
	{
		rank: 2,
		score: 180,
		team: mockTeamInfos[1],
		reviewedPullRequests: [],
		numberOfReviewedPRs: 15,
		numberOfApprovals: 7,
		numberOfChangeRequests: 3,
		numberOfComments: 5,
		numberOfUnknowns: 1,
		numberOfCodeComments: 10,
	},
	{
		rank: 3,
		score: 120,
		team: mockTeamInfos[2],
		reviewedPullRequests: [],
		numberOfReviewedPRs: 10,
		numberOfApprovals: 4,
		numberOfChangeRequests: 2,
		numberOfComments: 3,
		numberOfUnknowns: 0,
		numberOfCodeComments: 6,
	},
];

const meta: Meta<typeof TeamLeaderboardPage> = {
	component: TeamLeaderboardPage,
	parameters: {
		layout: "fullscreen",
	},
	tags: ["autodocs"],
};

export default meta;
type Story = StoryObj<typeof TeamLeaderboardPage>;

export const Default: Story = {
	args: {
		teamLeaderboard: mockTeamLeaderboard,
		isLoading: false,
		teams: mockTeams,
		selectedTeam: "all",
		selectedSort: "SCORE",
	},
};

export const WithCurrentTeam: Story = {
	args: {
		teamLeaderboard: mockTeamLeaderboard,
		isLoading: false,
		teams: mockTeams,
		selectedTeam: "Backend",
		selectedSort: "SCORE",
		currentTeam: mockTeamInfos[1], // Highlight Backend team
	},
};

export const Loading: Story = {
	args: {
		isLoading: true,
		teamLeaderboard: [],
		teams: mockTeams,
		selectedTeam: "all",
		selectedSort: "SCORE",
	},
};

export const EmptyLeaderboard: Story = {
	args: {
		teamLeaderboard: [],
		isLoading: false,
		teams: mockTeams,
		selectedTeam: "all",
		selectedSort: "SCORE",
	},
};

export const WithSchedule: Story = {
	args: {
		teamLeaderboard: mockTeamLeaderboard,
		isLoading: false,
		teams: mockTeams,
		selectedTeam: "all",
		selectedSort: "SCORE",
		leaderboardSchedule: {
			day: 1,
			hour: 12,
			minute: 30,
		},
	},
};
