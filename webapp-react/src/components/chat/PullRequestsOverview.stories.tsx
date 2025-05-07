import type { Meta, StoryObj } from '@storybook/react';
import { PullRequestsOverview } from './PullRequestsOverview';

const meta: Meta<typeof PullRequestsOverview> = {
  title: 'Components/Chat/PullRequestsOverview',
  component: PullRequestsOverview,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
};

export default meta;
type Story = StoryObj<typeof PullRequestsOverview>;

export const MultiplePullRequests: Story = {
  args: {
    pullRequests: [
      {
        url: 'https://github.com/org/repo/pull/123',
        repo: 'org/repo',
        number: 123,
        title: 'Feature: Add new authentication flow with OAuth provider integration',
        status: 'OPEN',
        created_at: '2023-05-10T14:30:00Z'
      },
      {
        url: 'https://github.com/org/repo/pull/124',
        repo: 'org/repo',
        number: 124,
        title: 'Fix: Resolve memory leak in useEffect cleanup function',
        status: 'OPEN',
        created_at: '2023-05-12T09:15:00Z'
      },
      {
        url: 'https://github.com/org/design-system/pull/45',
        repo: 'org/design-system',
        number: 45,
        title: 'Docs: Update component documentation with improved examples and API references',
        status: 'CLOSED',
        created_at: '2023-05-08T11:20:00Z'
      },
      {
        url: 'https://github.com/org/repo/pull/125',
        repo: 'org/repo',
        number: 125,
        title: 'Refactor: Optimize rendering performance in data grid component',
        status: 'MERGED',
        created_at: '2023-05-07T16:45:00Z'
      }
    ]
  }
};

export const SinglePullRequest: Story = {
  args: {
    pullRequests: [
      {
        url: 'https://github.com/org/repo/pull/126',
        repo: 'org/repo',
        number: 126,
        title: 'Feature: Add dark mode support with system preference detection',
        status: 'OPEN',
        created_at: '2023-05-14T15:45:00Z'
      }
    ]
  }
};

export const EmptyPullRequests: Story = {
  args: {
    pullRequests: []
  }
};