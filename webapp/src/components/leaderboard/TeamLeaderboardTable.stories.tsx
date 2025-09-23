import type { Meta, StoryObj } from '@storybook/react';
import { fn } from '@storybook/test';
import { TeamLeaderboardTable } from './TeamLeaderboardTable.tsx';
import type { TeamLeaderboardEntry,
  TeamInfo,
  RepositoryInfo,
  LabelInfo,
  PullRequestInfo,
} from '@/api/types.gen.ts';

// --- Greek Mythology Inspired Mock Data ---
const mockRepositories: RepositoryInfo[] = [
  {
    id: 101,
    name: 'olympus-api',
    nameWithOwner: 'gods/olympus-api',
    description: 'Backend for Mount Olympus operations',
    htmlUrl: 'https://github.com/gods/olympus-api',
  },
  {
    id: 102,
    name: 'underworld-service',
    nameWithOwner: 'gods/underworld-service',
    description: 'Service for Underworld management',
    htmlUrl: 'https://github.com/gods/underworld-service',
  },
  {
    id: 103,
    name: 'labyrinth-core',
    nameWithOwner: 'gods/labyrinth-core',
    description: 'Core logic for the Labyrinth',
    htmlUrl: 'https://github.com/gods/labyrinth-core',
  },
];

const mockLabels: LabelInfo[] = [
  { id: 201, name: 'thunder', color: '#1e90ff' },
  { id: 202, name: 'cerberus', color: '#8b0000' },
  { id: 203, name: 'wisdom', color: '#228b22' },
  { id: 204, name: 'maze', color: '#daa520' },
];

const mockMembers = [
  {
    id: 301,
    login: 'zeus.king',
    name: 'Zeus',
    avatarUrl: 'https://http.cat/200',
    htmlUrl: 'https://github.com/zeus',
  },
  {
    id: 302,
    login: 'hades.lord',
    name: 'Hades',
    avatarUrl: 'https://http.cat/404',
    htmlUrl: 'https://github.com/hades',
  },
  {
    id: 303,
    login: 'athena.wisdom',
    name: 'Athena',
    avatarUrl: 'https://http.cat/201',
    htmlUrl: 'https://github.com/athena',
  },
  {
    id: 304,
    login: 'poseidon.sea',
    name: 'Poseidon',
    avatarUrl: 'https://http.cat/202',
    htmlUrl: 'https://github.com/poseidon',
  },
  {
    id: 305,
    login: 'minotaur.labyrinth',
    name: 'Minotaur',
    avatarUrl: 'https://http.cat/500',
    htmlUrl: 'https://github.com/minotaur',
  },
  {
    id: 306,
    login: 'ariadne.thread',
    name: 'Ariadne',
    avatarUrl: 'https://http.cat/302',
    htmlUrl: 'https://github.com/ariadne',
  },
];

const mockPRs: PullRequestInfo[] = [
  {
    id: 1001,
    number: 1,
    title: 'Add thunderbolt feature',
    state: 'OPEN',
    isDraft: false,
    isMerged: false,
    commentsCount: 5,
    author: mockMembers[0], // Zeus
    labels: [mockLabels[0]],
    assignees: [mockMembers[2]], // Athena
    repository: mockRepositories[0],
    additions: 100,
    deletions: 10,
    mergedAt: undefined,
    closedAt: undefined,
    htmlUrl: 'https://github.com/gods/olympus-api/pull/1',
    createdAt: new Date('2025-09-01'),
    updatedAt: new Date('2025-09-02'),
  },
  {
    id: 1002,
    number: 2,
    title: 'Refactor wisdom logic',
    state: 'CLOSED',
    isDraft: false,
    isMerged: true,
    commentsCount: 8,
    author: mockMembers[2], // Athena
    labels: [mockLabels[2]],
    assignees: [mockMembers[0]], // Zeus
    repository: mockRepositories[0],
    additions: 200,
    deletions: 20,
    mergedAt: new Date('2025-09-03'),
    closedAt: new Date('2025-09-03'),
    htmlUrl: 'https://github.com/gods/olympus-api/pull/2',
    createdAt: new Date('2025-09-02'),
    updatedAt: new Date('2025-09-03'),
  },
  {
    id: 2001,
    number: 1,
    title: 'Cerberus guard implementation',
    state: 'CLOSED',
    isDraft: false,
    isMerged: true,
    commentsCount: 3,
    author: mockMembers[1], // Hades
    labels: [mockLabels[1]],
    assignees: [],
    repository: mockRepositories[1],
    additions: 50,
    deletions: 5,
    mergedAt: new Date('2025-09-04'),
    closedAt: new Date('2025-09-04'),
    htmlUrl: 'https://github.com/gods/underworld-service/pull/1',
    createdAt: new Date('2025-09-03'),
    updatedAt: new Date('2025-09-04'),
  },
  {
    id: 3001,
    number: 1,
    title: 'Optimize maze algorithm',
    state: 'OPEN',
    isDraft: true,
    isMerged: false,
    commentsCount: 2,
    author: mockMembers[5], // Ariadne
    labels: [mockLabels[3]],
    assignees: [mockMembers[4]], // Minotaur
    repository: mockRepositories[2],
    additions: 80,
    deletions: 8,
    mergedAt: undefined,
    closedAt: undefined,
    htmlUrl: 'https://github.com/gods/labyrinth-core/pull/1',
    createdAt: new Date('2025-09-01'),
    updatedAt: new Date('2025-09-02'),
  },
  {
    id: 3002,
    number: 2,
    title: 'Minotaur bugfix',
    state: 'CLOSED',
    isDraft: false,
    isMerged: true,
    commentsCount: 4,
    author: mockMembers[4], // Minotaur
    labels: [mockLabels[3]],
    assignees: [mockMembers[5]], // Ariadne
    repository: mockRepositories[2],
    additions: 30,
    deletions: 3,
    mergedAt: new Date('2025-09-05'),
    closedAt: new Date('2025-09-05'),
    htmlUrl: 'https://github.com/gods/labyrinth-core/pull/2',
    createdAt: new Date('2025-09-04'),
    updatedAt: new Date('2025-09-05'),
  },
];

const mockTeams: TeamInfo[] = [
  {
    id: 401,
    name: 'Olympians',
    repositories: [mockRepositories[0]],
    labels: [mockLabels[0], mockLabels[2]],
    members: [mockMembers[0], mockMembers[2], mockMembers[3]], // Zeus, Athena, Poseidon
    hidden: false,
    membershipCount: 0,
    repoPermissionCount: 0
  },
  {
    id: 402,
    name: 'Underworld Lords',
    repositories: [mockRepositories[1]],
    labels: [mockLabels[1]],
    members: [mockMembers[1]], // Hades
    hidden: false,
    membershipCount: 0,
    repoPermissionCount: 0
  },
  {
    id: 403,
    name: 'Labyrinth Keepers',
    repositories: [mockRepositories[2]],
    labels: [mockLabels[3]],
    members: [mockMembers[4], mockMembers[5]], // Minotaur, Ariadne
    hidden: false,
    membershipCount: 0,
    repoPermissionCount: 0
  }
];

const mockLeaderboard: TeamLeaderboardEntry[] = [
  {
    rank: 1,
    score: 2001,
    team: mockTeams[0],
    reviewedPullRequests: [mockPRs[0], mockPRs[1]],
    numberOfReviewedPRs: 2,
    numberOfApprovals: 12,
    numberOfChangeRequests: 3,
    numberOfComments: 15,
    numberOfUnknowns: 1,
    numberOfCodeComments: 8,
  },
  {
    rank: 2,
    score: 2002,
    team: mockTeams[1],
    reviewedPullRequests: [mockPRs[2]],
    numberOfReviewedPRs: 1,
    numberOfApprovals: 5,
    numberOfChangeRequests: 1,
    numberOfComments: 7,
    numberOfUnknowns: 0,
    numberOfCodeComments: 3,
  },
  {
    rank: 3,
    score: 2003,
    team: mockTeams[2],
    reviewedPullRequests: [mockPRs[3], mockPRs[4]],
    numberOfReviewedPRs: 2,
    numberOfApprovals: 8,
    numberOfChangeRequests: 2,
    numberOfComments: 10,
    numberOfUnknowns: 2,
    numberOfCodeComments: 5,
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

export const Default: Story = {
  args: {
    isLoading: false,
    teamLeaderboard: mockLeaderboard,
  },
};

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

// export const WithInteraction: Story = {
//   args: {
//     isLoading: false,
//     teamLeaderboard: mockLeaderboard,
//     onTeamClick: fn(),
//   },
// };
