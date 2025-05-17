import type { Meta, StoryObj } from '@storybook/react';
import { LeaderboardOverview } from './LeaderboardOverview';
import { addDays, addHours, subDays } from 'date-fns';

/**
 * Overview component that displays a user's current performance in the leaderboard.
 * Shows rank, score, and time until the current leaderboard period ends.
 */
const meta = {
  component: LeaderboardOverview,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered'
  },
  argTypes: {
    leaderboardEntry: {
      description: 'Current user\'s leaderboard entry with rank, score and details',
      control: 'object',
    },
    leaguePoints: {
      description: 'User\'s current league points',
      control: 'number',
    },
    leaderboardEnd: {
      description: 'ISO date string for when the current leaderboard period ends',
      control: 'text',
    },
    leaguePointsChange: {
      description: 'Recent change in league points (positive or negative)',
      control: 'number',
    }
  }
} satisfies Meta<typeof LeaderboardOverview>;

export default meta;

type Story = StoryObj<typeof meta>;

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

/**
 * Default view showing current rank and score with positive league points change.
 */
export const Default: Story = {
  args: {
    leaderboardEntry: mockLeaderboardEntry,
    leaguePoints: 750,
    leaderboardEnd: new Date().toISOString(),
    leaguePointsChange: 25
  }
};

/**
 * Shows overview with leaderboard ending in several days.
 */
export const WithFutureEnd: Story = {
  args: {
    leaderboardEntry: mockLeaderboardEntry,
    leaguePoints: 750,
    leaderboardEnd: addDays(new Date(), 5).toISOString(),
    leaguePointsChange: 25
  }
};

/**
 * Shows overview with leaderboard ending soon (less than 24 hours).
 * Highlights urgency to improve rank before period ends.
 */
export const WithEndingSoon: Story = {
  args: {
    leaderboardEntry: mockLeaderboardEntry,
    leaguePoints: 750,
    leaderboardEnd: addHours(new Date(), 10).toISOString(),
    leaguePointsChange: 25
  }
};

/**
 * Shows overview with negative league points change.
 */
export const WithNegativeChange: Story = {
  args: {
    leaderboardEntry: mockLeaderboardEntry,
    leaguePoints: 720,
    leaderboardEnd: addDays(new Date(), 3).toISOString(),
    leaguePointsChange: -30
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
