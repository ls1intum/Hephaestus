import type { Meta, StoryObj } from '@storybook/react';
import { Dashboard } from './Dashboard';
import { ActivityFeed } from './ActivityFeed';
import { PullRequestStats } from './PullRequestStats';
import { RepositoryStats } from './RepositoryStats';
import { BadPractices } from './BadPractices';
import { createContext } from 'react';

// Create context for child components
export const DashboardContext = createContext({
  ActivityFeed,
  PullRequestStats,
  RepositoryStats,
  BadPractices
});

const meta: Meta<typeof Dashboard> = {
  title: 'Pages/Dashboard',
  component: Dashboard,
  parameters: {
    layout: 'fullscreen',
  },
};

export default meta;
type Story = StoryObj<typeof Dashboard>;

const mockBadPractices = [
  {
    id: '1',
    type: 'Security',
    message: 'Hardcoded API credentials detected',
    repository: 'auth-service',
    filePath: 'src/config/auth.js',
    severity: 'high' as const,
  },
  {
    id: '2',
    type: 'Bug',
    message: 'Possible memory leak in component lifecycle',
    repository: 'frontend-app',
    filePath: 'src/components/Dashboard.tsx',
    severity: 'medium' as const,
  }
];

export const Default: Story = {
  args: {
    username: 'johndoe',
    repositories: 12,
    pullRequests: 23,
    commits: 156,
    activeDays: 21,
    streak: 7,
    badPractices: mockBadPractices
  },
};

export const NewUser: Story = {
  args: {
    username: 'newbie',
    repositories: 2,
    pullRequests: 0,
    commits: 5,
    activeDays: 2,
    streak: 1,
    badPractices: []
  },
};

export const PowerUser: Story = {
  args: {
    username: 'codemaster',
    repositories: 48,
    pullRequests: 127,
    commits: 893,
    activeDays: 65,
    streak: 32,
    badPractices: mockBadPractices
  },
};