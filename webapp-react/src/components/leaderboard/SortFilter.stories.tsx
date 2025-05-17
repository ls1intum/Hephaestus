import type { Meta, StoryObj } from '@storybook/react';
import { fn } from '@storybook/test';
import { SortFilter } from './SortFilter';

/**
 * Sort filter component for controlling leaderboard ordering.
 * Allows users to sort by different metrics like Score or League Points.
 */
const meta = {
  component: SortFilter,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  argTypes: {
    selectedSort: {
      control: 'select',
      options: ['SCORE', 'LEAGUE_POINTS'],
      description: 'The currently selected sort option',
    },
    onSortChange: {
      description: 'Callback when sort option changes',
      action: 'sort changed',
    }
  },
  args: {
    onSortChange: fn(),
  }
} satisfies Meta<typeof SortFilter>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default sort by total score, showing player performance across all metrics.
 */
export const Default: Story = {
  args: {
    selectedSort: 'SCORE',
  },
};

/**
 * Sort by league points, emphasizing progress within the league system.
 */
export const SortByApprovals: Story = {
  args: {
    selectedSort: 'LEAGUE_POINTS',
  },
};