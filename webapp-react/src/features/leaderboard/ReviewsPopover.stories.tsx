import type { Meta, StoryObj } from '@storybook/react';
import { ReviewsPopover } from './ReviewsPopover';
import type { PullRequestInfo } from '@/api/types.gen';

const mockPullRequests: PullRequestInfo[] = [
  {
    id: 1,
    number: 101,
    title: "Fix login bug",
    state: "CLOSED",
    isDraft: false,
    isMerged: true,
    commentsCount: 3,
    additions: 50,
    deletions: 10,
    htmlUrl: "https://github.com/org/repo/pull/101",
  },
  {
    id: 2,
    number: 102,
    title: "Update documentation",
    state: "CLOSED",
    isDraft: false,
    isMerged: true,
    commentsCount: 1,
    additions: 120,
    deletions: 5,
    htmlUrl: "https://github.com/org/repo/pull/102",
  },
];

const meta: Meta<typeof ReviewsPopover> = {
  title: 'Leaderboard/ReviewsPopover',
  component: ReviewsPopover,
  tags: ['autodocs'],
  argTypes: {
    highlight: { control: 'boolean' }
  },
  parameters: {
    layout: 'centered',
  },
};

export default meta;
type Story = StoryObj<typeof ReviewsPopover>;

export const WithReviews: Story = {
  args: {
    reviewedPRs: mockPullRequests,
    highlight: false,
  },
};

export const Highlighted: Story = {
  args: {
    reviewedPRs: mockPullRequests,
    highlight: true,
  },
};

export const NoReviews: Story = {
  args: {
    reviewedPRs: [],
    highlight: false,
  },
};