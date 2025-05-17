import type { Meta, StoryObj } from '@storybook/react';
import { fn } from '@storybook/test';
import { TimeframeFilter } from './TimeframeFilter';

/**
 * Timeframe filter component for selecting date ranges to filter leaderboard data.
 * Provides preset options (this week, last week, etc.) and custom date range selection.
 */
const meta = {
  component: TimeframeFilter,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  argTypes: {
    onTimeframeChange: {
      description: 'Callback fired when timeframe selection changes',
      action: 'timeframe changed',
    },
    initialAfterDate: {
      description: 'Initial start date in ISO format',
      control: 'text',
    },
    initialBeforeDate: {
      description: 'Initial end date in ISO format',
      control: 'text',
    },
    leaderboardSchedule: {
      description: 'Schedule information for leaderboard updates',
      control: 'object',
    }
  },
  args: {
    onTimeframeChange: fn(),
  }
} satisfies Meta<typeof TimeframeFilter>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default view without any schedule or initial date range.
 * Shows the standard timeframe selection options.
 */
export const Default: Story = {
  args: {},
};

/**
 * Shows timeframe filter with leaderboard schedule information.
 * This helps users understand when the next leaderboard reset occurs.
 */
export const WithSchedule: Story = {
  args: {
    leaderboardSchedule: {
      day: 1, // Monday
      hour: 9,
      minute: 0,
      formatted: 'Mondays at 09:00'
    }
  },
};

export const CustomSchedule: Story = {
  args: {
    leaderboardSchedule: {
      day: 5, // Friday
      hour: 16,
      minute: 30,
      formatted: 'Fridays at 16:30'
    }
  },
};