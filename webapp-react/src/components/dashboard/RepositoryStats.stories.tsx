import type { Meta, StoryObj } from '@storybook/react';
import { RepositoryStats } from './RepositoryStats';

const meta: Meta<typeof RepositoryStats> = {
  title: 'Dashboard/RepositoryStats',
  component: RepositoryStats,
  parameters: {
    layout: 'centered',
  },
};

export default meta;
type Story = StoryObj<typeof RepositoryStats>;

const sampleRepositories = [
  { 
    name: 'frontend', 
    nameWithOwner: 'acme/frontend',
    commits: 78,
    pullRequests: 23,
    issues: 15
  },
  { 
    name: 'backend', 
    nameWithOwner: 'acme/backend',
    commits: 56,
    pullRequests: 18,
    issues: 12
  },
  { 
    name: 'design-system', 
    nameWithOwner: 'acme/design-system',
    commits: 45,
    pullRequests: 9,
    issues: 6
  },
  { 
    name: 'docs', 
    nameWithOwner: 'acme/docs',
    commits: 32,
    pullRequests: 5,
    issues: 3
  },
  { 
    name: 'mobile-app', 
    nameWithOwner: 'acme/mobile-app',
    commits: 25,
    pullRequests: 7,
    issues: 8
  },
  { 
    name: 'infrastructure', 
    nameWithOwner: 'acme/infrastructure',
    commits: 12,
    pullRequests: 3,
    issues: 5
  }
];

export const Default: Story = {
  args: {
    repositories: sampleRepositories,
  },
};

export const FewRepositories: Story = {
  args: {
    repositories: sampleRepositories.slice(0, 2),
  },
};

export const EmptyState: Story = {
  args: {
    repositories: [],
  },
};

export const CommitHeavy: Story = {
  args: {
    repositories: [
      { 
        name: 'frontend', 
        nameWithOwner: 'acme/frontend',
        commits: 145,
        pullRequests: 12,
        issues: 8
      },
      { 
        name: 'backend', 
        nameWithOwner: 'acme/backend',
        commits: 122,
        pullRequests: 9,
        issues: 5
      },
      {
        name: 'design-system', 
        nameWithOwner: 'acme/design-system',
        commits: 98,
        pullRequests: 5,
        issues: 3
      }
    ],
  },
};

export const PRHeavy: Story = {
  args: {
    repositories: [
      { 
        name: 'frontend', 
        nameWithOwner: 'acme/frontend',
        commits: 45,
        pullRequests: 78,
        issues: 12
      },
      { 
        name: 'backend', 
        nameWithOwner: 'acme/backend',
        commits: 32,
        pullRequests: 65,
        issues: 8
      },
      {
        name: 'design-system', 
        nameWithOwner: 'acme/design-system',
        commits: 28,
        pullRequests: 42,
        issues: 5
      }
    ],
  },
};