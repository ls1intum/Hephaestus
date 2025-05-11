import type { Meta, StoryObj } from '@storybook/react';
import { PullRequestStats } from './PullRequestStats';

const meta: Meta<typeof PullRequestStats> = {
  title: 'Dashboard/PullRequestStats',
  component: PullRequestStats,
  parameters: {
    layout: 'centered',
  },
};

export default meta;
type Story = StoryObj<typeof PullRequestStats>;

export const Default: Story = {
  args: {
    data: {
      open: 5,
      closed: 3,
      merged: 12
    }
  },
};

export const MostlyOpen: Story = {
  args: {
    data: {
      open: 15,
      closed: 2,
      merged: 8
    }
  },
};

export const MostlyClosed: Story = {
  args: {
    data: {
      open: 2,
      closed: 18,
      merged: 5
    }
  },
};

export const MostlyMerged: Story = {
  args: {
    data: {
      open: 4,
      closed: 6,
      merged: 25
    }
  },
};

export const EmptyState: Story = {
  args: {
    data: {
      open: 0,
      closed: 0,
      merged: 0
    }
  },
};