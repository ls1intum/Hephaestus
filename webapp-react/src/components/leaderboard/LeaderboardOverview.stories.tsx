import type { Meta, StoryObj } from '@storybook/react';
import { LeaderboardOverview } from './LeaderboardOverview';
import { addDays, addHours, subDays } from 'date-fns';

const meta: Meta<typeof LeaderboardOverview> = {
  component: LeaderboardOverview,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered'
  }
};

export default meta;

type Story = StoryObj<typeof LeaderboardOverview>;

const mockLeaderboardEntry = {
  rank: 2,
  score: 90,
  user: {
    id: 1,
    login: 'johndoe',
    avatarUrl: 'https://github.com/github.png',
    name: 'John Doe',
    htmlUrl: 'https://github.com/johndoe',
    leaguePoints: 750
  },
  reviewedPullRequests: [],
  numberOfReviewedPRs: 8,
  numberOfApprovals: 1,
  numberOfChangeRequests: 5,
  numberOfComments: 2,
  numberOfUnknowns: 0,
  numberOfCodeComments: 3
};

export const Default: Story = {
  args: {
    leaderboardEntry: mockLeaderboardEntry,
    leaguePoints: 750,
    leaderboardEnd: new Date().toISOString(),
    leaguePointsChange: 25
  }
};

export const WithFutureEnd: Story = {
  args: {
    leaderboardEntry: mockLeaderboardEntry,
    leaguePoints: 750,
    leaderboardEnd: addDays(new Date(), 5).toISOString(),
    leaguePointsChange: 25
  }
};

export const WithEndingSoon: Story = {
  args: {
    leaderboardEntry: mockLeaderboardEntry,
    leaguePoints: 750,
    leaderboardEnd: addHours(new Date(), 10).toISOString(),
    leaguePointsChange: 25
  }
};

export const WithPastEnd: Story = {
  args: {
    leaderboardEntry: mockLeaderboardEntry,
    leaguePoints: 750,
    leaderboardEnd: subDays(new Date(), 1).toISOString(),
    leaguePointsChange: -10
  }
};

export const WithNegativePointsChange: Story = {
  args: {
    leaderboardEntry: mockLeaderboardEntry,
    leaguePoints: 750,
    leaderboardEnd: addDays(new Date(), 2).toISOString(),
    leaguePointsChange: -15
  }
};

export const WithNoPointsChange: Story = {
  args: {
    leaderboardEntry: mockLeaderboardEntry,
    leaguePoints: 750,
    leaderboardEnd: addDays(new Date(), 2).toISOString(),
    leaguePointsChange: 0
  }
};
