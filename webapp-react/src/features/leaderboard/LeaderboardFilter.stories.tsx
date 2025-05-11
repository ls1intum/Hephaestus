import type { Meta, StoryObj } from '@storybook/react';
import { LeaderboardFilter } from './LeaderboardFilter';

const meta: Meta<typeof LeaderboardFilter> = {
  title: 'Leaderboard/LeaderboardFilter',
  component: LeaderboardFilter,
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof LeaderboardFilter>;

export const Default: Story = {
  args: {
    teams: ['Frontend', 'Backend', 'DevOps', 'QA', 'Design'],
    selectedTeam: 'all',
    selectedSort: 'SCORE',
  },
};

export const WithSelectedFilters: Story = {
  args: {
    teams: ['Frontend', 'Backend', 'DevOps', 'QA', 'Design'],
    selectedTeam: 'Frontend',
    selectedSort: 'LEAGUE_POINTS',
  },
};