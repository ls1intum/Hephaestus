import type { Meta, StoryObj } from '@storybook/react';
import { SortFilter } from './SortFilter';

const meta: Meta<typeof SortFilter> = {
  title: 'Leaderboard/Filters/SortFilter',
  component: SortFilter,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
};

export default meta;
type Story = StoryObj<typeof SortFilter>;

export const Default: Story = {
  args: {
    selectedSort: 'SCORE',
  },
};

export const SortByApprovals: Story = {
  args: {
    selectedSort: 'APPROVALS',
  },
};