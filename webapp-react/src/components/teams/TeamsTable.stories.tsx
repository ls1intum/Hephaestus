import type { Meta, StoryObj } from '@storybook/react';
import { TeamsTable } from './TeamsTable';
import type { TeamInfo } from './types';
import { action } from '@storybook/addon-actions';

const meta: Meta<typeof TeamsTable> = {
  title: 'Components/Teams/TeamsTable',
  component: TeamsTable,
  tags: ['autodocs'],
  parameters: {
    layout: 'padded',
  },
};

export default meta;
type Story = StoryObj<typeof TeamsTable>;

// Sample data for teams
const sampleTeams: TeamInfo[] = [
  {
    id: 1,
    name: 'Frontend Team',
    color: '3b82f6',
    hidden: false,
    repositories: [
      { id: 101, nameWithOwner: 'organization/frontend' },
      { id: 102, nameWithOwner: 'organization/design-system' },
      { id: 103, nameWithOwner: 'organization/component-library' }
    ],
    labels: [
      { id: 201, name: 'frontend', color: '3b82f6', description: 'Frontend related issues' },
      { id: 202, name: 'ui', color: 'd946ef', description: 'UI improvements' },
      { id: 203, name: 'bug', color: 'ef4444', description: 'Something isn\'t working' }
    ],
    members: [
      { id: 301, login: 'sarah', name: 'Sarah Johnson', avatarUrl: 'https://i.pravatar.cc/150?u=sarah' },
      { id: 302, login: 'michael', name: 'Michael Chen', avatarUrl: 'https://i.pravatar.cc/150?u=michael' },
      { id: 303, login: 'emma', name: 'Emma Wilson', avatarUrl: 'https://i.pravatar.cc/150?u=emma' },
      { id: 304, login: 'david', name: 'David Rodriguez', avatarUrl: 'https://i.pravatar.cc/150?u=david' }
    ]
  },
  {
    id: 2,
    name: 'Backend Team',
    color: '10b981',
    hidden: false,
    repositories: [
      { id: 104, nameWithOwner: 'organization/api' },
      { id: 105, nameWithOwner: 'organization/server' }
    ],
    labels: [
      { id: 204, name: 'backend', color: '10b981', description: 'Backend related issues' },
      { id: 205, name: 'database', color: '6366f1', description: 'Database changes' }
    ],
    members: [
      { id: 305, login: 'alex', name: 'Alex Kim', avatarUrl: 'https://i.pravatar.cc/150?u=alex' },
      { id: 306, login: 'jamie', name: 'Jamie Taylor', avatarUrl: 'https://i.pravatar.cc/150?u=jamie' }
    ]
  },
  {
    id: 3,
    name: 'DevOps Team',
    color: 'f59e0b',
    hidden: true,
    repositories: [
      { id: 106, nameWithOwner: 'organization/infrastructure' },
      { id: 107, nameWithOwner: 'organization/deployment' },
      { id: 108, nameWithOwner: 'organization/monitoring' }
    ],
    labels: [
      { id: 206, name: 'devops', color: 'f59e0b', description: 'DevOps related issues' },
      { id: 207, name: 'ci-cd', color: '8b5cf6', description: 'CI/CD pipeline' }
    ],
    members: [
      { id: 307, login: 'chris', name: 'Chris Patel', avatarUrl: 'https://i.pravatar.cc/150?u=chris' },
      { id: 308, login: 'pat', name: 'Pat Thompson', avatarUrl: 'https://i.pravatar.cc/150?u=pat' }
    ]
  }
];

export const WithTeams: Story = {
  args: {
    teams: sampleTeams,
    isLoading: false,
    onAddTeam: action('add-team'),
    onEditTeam: action('edit-team'),
    onDeleteTeam: action('delete-team'),
    onToggleVisibility: action('toggle-visibility'),
    onAddLabel: action('add-label'),
    onAddRepository: action('add-repository')
  }
};

export const Loading: Story = {
  args: {
    teams: [],
    isLoading: true
  }
};

export const EmptyState: Story = {
  args: {
    teams: [],
    isLoading: false,
    onAddTeam: action('add-team')
  }
};