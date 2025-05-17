import type { Meta, StoryObj } from '@storybook/react';
import { fn } from '@storybook/test';
import { TeamFilter } from './TeamFilter';

/**
 * Team filter component allowing users to filter leaderboard results by team.
 * Includes an "All Teams" option and a list of available teams.
 */
const meta = {
  component: TeamFilter,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  argTypes: {
    teams: {
      description: 'Array of available teams to filter by',
      control: 'object',
    },
    selectedTeam: {
      description: 'Currently selected team filter (defaults to "all")',
      control: 'text',
    },
    onTeamChange: {
      description: 'Callback when team selection changes',
      action: 'team changed',
    }
  },
  args: {
    onTeamChange: fn(),
  }
} satisfies Meta<typeof TeamFilter>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default view showing all teams option selected with multiple teams available.
 */
export const Default: Story = {
  args: {
    teams: ['Frontend', 'Backend', 'DevOps', 'QA', 'Design'],
    selectedTeam: 'all',
  },
};

/**
 * Single team selected to filter results.
 */
export const TeamSelected: Story = {
  args: {
    teams: ['Frontend', 'Backend', 'DevOps', 'QA', 'Design'],
    selectedTeam: 'Frontend',
  },
};

/**
 * Empty state with no teams available to filter by.
 */
export const NoTeams: Story = {
  args: {
    teams: [],
    selectedTeam: 'all',
  },
};

export const WithSelectedTeam: Story = {
  args: {
    teams: ['Frontend', 'Backend', 'DevOps', 'QA', 'Design'],
    selectedTeam: 'Frontend',
  },
};

export const EmptyTeamList: Story = {
  args: {
    teams: [],
    selectedTeam: 'all',
  },
};