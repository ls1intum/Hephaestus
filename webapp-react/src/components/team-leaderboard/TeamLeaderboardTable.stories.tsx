import type { Meta, StoryObj } from '@storybook/react';
import { fn } from '@storybook/test';
import { TeamLeaderboardTable } from './TeamLeaderboardTable';
import type { TeamLeaderboardEntry, TeamInfo, PullRequestInfo, UserInfo, LabelInfo, RepositoryInfo } from '@/api/types.gen';

const mockMembers: UserInfo[] = [
  {
    id: 1,
    login: 'alice',
    avatarUrl: 'https://avatars.githubusercontent.com/u/1?v=4',
    name: 'Alice Developer',
    htmlUrl: 'https://github.com/alice',
    leaguePoints: 500,
  },
  {
    id: 2,
    login: 'bob',
    avatarUrl: 'https://avatars.githubusercontent.com/u/2?v=4',
    name: 'Bob Builder',
    htmlUrl: 'https://github.com/bob',
    leaguePoints: 400,
  },
];

const mockRepositories: RepositoryInfo[] = [
  {
    id: 1,
    name: 'hephaestus',
    nameWithOwner: 'awesome-org/hephaestus',
    description: 'Code review analytics platform',
    htmlUrl: 'https://github.com/awesome-org/hephaestus',
  },
];

const mockLabels: LabelInfo[] = [
  { id: 1, name: 'frontend', color: '#ffcc00' },
  { id: 2, name: 'backend', color: '#00ccff' },
];

const mockPRs: PullRequestInfo[] = [
  {
    id: 101,
    number: 42,
    title: 'Add new leaderboard feature',
    state: 'CLOSED',
    isDraft: false,
    isMerged: true,
    commentsCount: 5,
    author: mockMembers[0],
    labels: [mockLabels[0]],
    assignees: [mockMembers[1]],
    repository: mockRepositories[0],
    additions: 120,
    deletions: 10,
    mergedAt: new Date('2025-08-01'),
    closedAt: new Date('2025-08-02'),
    htmlUrl: 'https://github.com/awesome-org/hephaestus/pull/42',
    createdAt: new Date('2025-07-30'),
    updatedAt: new Date('2025-08-02'),
  },
];

const mockTeams: TeamInfo[] = [
  {
    id: 1,
    name: 'Frontend Masters',
    color: '#ffcc00',
    repositories: mockRepositories,
    labels: mockLabels,
    members: mockMembers,
    hidden: false,
  },
  {
    id: 2,
    name: 'Backend Gurus',
    color: '#00ccff',
    repositories: mockRepositories,
    labels: mockLabels,
    members: mockMembers,
    hidden: false,
  },
];

const mockLeaderboard: TeamLeaderboardEntry[] = [
  {
    rank: 1,
    score: 150,
    team: mockTeams[0],
    reviewedPullRequests: mockPRs,
    numberOfReviewedPRs: 10,
    numberOfApprovals: 7,
    numberOfChangeRequests: 2,
    numberOfComments: 5,
    numberOfUnknowns: 0,
    numberOfCodeComments: 3,
  },
  {
    rank: 2,
    score: 120,
    team: mockTeams[1],
    reviewedPullRequests: [],
    numberOfReviewedPRs: 8,
    numberOfApprovals: 5,
    numberOfChangeRequests: 1,
    numberOfComments: 2,
    numberOfUnknowns: 1,
    numberOfCodeComments: 2,
  },
];

const meta = {
    component: TeamLeaderboardTable,
    tags: ["autodocs"],
    parameters: {
        layout: "padded",
    },
    argTypes: {
        teamLeaderboard: {
            description: "Array of team leaderboard entries to display",
        },
        isLoading: {
            description: "Whether the leaderboard data is currently loading",
            control: "boolean",
        },
        currentTeam: {
            description: "Currently logged in users teams info to highlight",
        },
        onTeamClick: {
            description: "Callback function when a team row is clicked (currently unused)",
            action: "clicked",
        },
    },
    args: {
        onTeamClick: fn(),
    },
} satisfies Meta<typeof TeamLeaderboardTable>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Loading: Story = {
  args: {
    isLoading: true,
    teamLeaderboard: [],
  },
};

export const Empty: Story = {
  args: {
    isLoading: false,
    teamLeaderboard: [],
  },
};

export const Populated: Story = {
  args: {
    isLoading: false,
    teamLeaderboard: mockLeaderboard,
  },
};

// export const WithInteraction: Story = {
//   args: {
//     isLoading: false,
//     teamLeaderboard: mockLeaderboard,
//     onTeamClick: fn(),
//   },
// };