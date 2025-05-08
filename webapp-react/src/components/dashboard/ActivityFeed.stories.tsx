import type { Meta, StoryObj } from '@storybook/react';
import { ActivityFeed } from './ActivityFeed';
import { subDays, subHours } from 'date-fns';

const meta = {
  title: 'Dashboard/ActivityFeed',
  component: ActivityFeed,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
} satisfies Meta<typeof ActivityFeed>;

export default meta;
type Story = StoryObj<typeof meta>;

const mockActivities = [
  {
    id: '1',
    type: 'commit' as const,
    title: 'Fixed bug in authentication flow',
    repository: 'user-service',
    timestamp: subDays(new Date(), 1),
    user: {
      login: 'johndoe',
      avatarUrl: 'https://github.com/identicons/app/oauth_app/1',
    },
  },
  {
    id: '2',
    type: 'pull_request' as const,
    title: 'Add new dashboard features',
    repository: 'frontend-app',
    timestamp: subDays(new Date(), 2),
    user: {
      login: 'janedoe',
      avatarUrl: 'https://github.com/identicons/app/oauth_app/2',
    },
  },
  {
    id: '3',
    type: 'merge' as const,
    title: 'Merged PR: Refactor database queries',
    repository: 'api-service',
    timestamp: subDays(new Date(), 3),
    user: {
      login: 'bobsmith',
      avatarUrl: 'https://github.com/identicons/app/oauth_app/3',
    },
  },
  {
    id: '4',
    type: 'comment' as const,
    title: 'Commented on issue #234',
    repository: 'documentation',
    timestamp: subDays(new Date(), 4),
    user: {
      login: 'alicewhite',
      avatarUrl: 'https://github.com/identicons/app/oauth_app/4',
    },
  },
  {
    id: '5',
    type: 'star' as const,
    title: 'Starred repository',
    repository: 'awesome-tools',
    timestamp: subDays(new Date(), 5),
    user: {
      login: 'markbrown',
      avatarUrl: 'https://github.com/identicons/app/oauth_app/5',
    },
  },
];

export const Default: Story = {
  args: {
    activities: mockActivities,
  },
};

export const WithNoActivity: Story = {
  args: {
    activities: [],
  },
};

export const WithSingleActivity: Story = {
  args: {
    activities: [
      {
        id: '1',
        type: 'commit' as const,
        title: 'Fixed critical security bug',
        repository: 'auth-service',
        timestamp: subHours(new Date(), 2),
        user: {
          login: 'securitydev',
          avatarUrl: 'https://github.com/identicons/app/oauth_app/6',
        },
      },
    ],
  },
};

export const WithoutUserInfo: Story = {
  args: {
    activities: [
      {
        id: '1',
        type: 'pull_request' as const,
        title: 'Automated PR: Update dependencies',
        repository: 'backend-api',
        timestamp: subHours(new Date(), 4),
      },
      {
        id: '2',
        type: 'comment' as const,
        title: 'Bot comment on issue #123',
        repository: 'ci-pipeline',
        timestamp: subHours(new Date(), 5),
      },
    ],
  },
};