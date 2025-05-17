import type { Meta, StoryObj } from '@storybook/react';
import { TeamFilter } from './TeamFilter';

const meta: Meta<typeof TeamFilter> = {
  component: TeamFilter,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
};

export default meta;
type Story = StoryObj<typeof TeamFilter>;

export const Default: Story = {
  args: {
    teams: ['Frontend', 'Backend', 'DevOps', 'QA', 'Design'],
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