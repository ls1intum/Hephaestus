import type { Meta, StoryObj } from '@storybook/react';
import { TimeframeFilter } from './TimeframeFilter';

const meta: Meta<typeof TimeframeFilter> = {
  title: 'Leaderboard/Filters/TimeframeFilter',
  component: TimeframeFilter,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
};

export default meta;
type Story = StoryObj<typeof TimeframeFilter>;

export const Default: Story = {
  args: {},
};

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