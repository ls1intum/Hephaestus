import type { Meta, StoryObj } from "@storybook/react";
import Leaderboard from "./Leaderboard";
import { type PullRequestInfo } from '@/lib/api/models';

// Helper function to create sample pull requests
const samplePullRequests = (count: number): PullRequestInfo[] => {
  return Array.from({ length: count }, (_, i) => ({
    id: i,
    number: i + 100,
    title: `Fix bug in component ${i + 1}`,
    state: i % 3 === 0 ? 'OPEN' : i % 3 === 1 ? 'CLOSED' : 'MERGED',
    isDraft: i % 5 === 0,
    isMerged: i % 3 === 2,
    commentsCount: Math.floor(Math.random() * 10),
    additions: Math.floor(Math.random() * 100) + 10,
    deletions: Math.floor(Math.random() * 50),
    htmlUrl: `https://github.com/ls1intum/hephaestus/pull/${i + 100}`,
  } as PullRequestInfo));
};

const meta: Meta<typeof Leaderboard> = {
  component: Leaderboard,
  tags: ['autodocs'],
  parameters: {
    layout: 'padded',
  },
  decorators: [
    (Story) => (
      <div className="border rounded-md p-4 bg-background">
        <Story />
      </div>
    ),
  ],
};

export default meta;
type Story = StoryObj<typeof Leaderboard>;

export const Default: Story = {
  args: {
    leaderboard: [
      {
        rank: 1,
        score: 520,
        user: {
          id: 0,
          leaguePoints: 2000,
          login: 'codeMaster',
          avatarUrl: '/images/alice_developer.jpg',
          name: 'Alice Developer',
          htmlUrl: 'https://github.com/alice'
        },
        reviewedPullRequests: samplePullRequests(10),
        numberOfReviewedPRs: 18,
        numberOfApprovals: 8,
        numberOfChangeRequests: 7,
        numberOfComments: 2,
        numberOfUnknowns: 1,
        numberOfCodeComments: 5
      },
      {
        rank: 2,
        score: 431,
        user: {
          id: 1,
          leaguePoints: 1000,
          login: 'devWizard',
          avatarUrl: '/images/bob_builder.jpg',
          name: 'Bob Builder',
          htmlUrl: 'https://github.com/bob'
        },
        reviewedPullRequests: samplePullRequests(4),
        numberOfReviewedPRs: 8,
        numberOfApprovals: 1,
        numberOfChangeRequests: 5,
        numberOfComments: 2,
        numberOfUnknowns: 0,
        numberOfCodeComments: 21
      },
      {
        rank: 3,
        score: 302,
        user: {
          id: 2,
          leaguePoints: 1500,
          login: 'codeNinja',
          avatarUrl: '/images/charlie_coder.jpg',
          name: 'Charlie Coder',
          htmlUrl: 'https://github.com/charlie'
        },
        reviewedPullRequests: samplePullRequests(3),
        numberOfReviewedPRs: 5,
        numberOfApprovals: 3,
        numberOfChangeRequests: 1,
        numberOfComments: 0,
        numberOfUnknowns: 0,
        numberOfCodeComments: 2
      }
    ]
  }
};

export const Empty: Story = {
  args: {
    leaderboard: []
  }
};

export const Loading: Story = {
  args: {
    leaderboard: [],
    isLoading: true
  }
};