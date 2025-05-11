import type { Meta, StoryObj } from '@storybook/react';
import { WorkspaceRepositories } from './WorkspaceRepositories';

const meta: Meta<typeof WorkspaceRepositories> = {
  title: 'Workspace/Repositories',
  component: WorkspaceRepositories,
  parameters: {
    layout: 'padded',
  },
};

export default meta;
type Story = StoryObj<typeof WorkspaceRepositories>;

const sampleRepositories = [
  { 
    id: 101, 
    name: 'frontend', 
    nameWithOwner: 'acme-corp/frontend',
    description: 'Frontend application built with React and TypeScript',
    isPrivate: false,
    stargazerCount: 42,
    forkCount: 12,
    openIssuesCount: 7,
    openPullRequestsCount: 3,
    url: 'https://github.com/acme-corp/frontend'
  },
  { 
    id: 102, 
    name: 'backend', 
    nameWithOwner: 'acme-corp/backend',
    description: 'Backend API server built with Node.js',
    isPrivate: true,
    stargazerCount: 28,
    forkCount: 5,
    openIssuesCount: 12,
    openPullRequestsCount: 2,
    url: 'https://github.com/acme-corp/backend'
  },
  { 
    id: 103, 
    name: 'docs', 
    nameWithOwner: 'acme-corp/docs',
    description: 'Documentation for all Acme Corp projects',
    isPrivate: false,
    stargazerCount: 15,
    forkCount: 8,
    openIssuesCount: 3,
    openPullRequestsCount: 1,
    url: 'https://github.com/acme-corp/docs'
  },
  { 
    id: 104, 
    name: 'mobile-app', 
    nameWithOwner: 'acme-corp/mobile-app',
    description: 'Mobile application built with React Native',
    isPrivate: true,
    stargazerCount: 19,
    forkCount: 3,
    openIssuesCount: 5,
    openPullRequestsCount: 0,
    url: 'https://github.com/acme-corp/mobile-app'
  }
];

export const Default: Story = {
  args: {
    isAdmin: false,
    initialRepositories: sampleRepositories,
  },
};

export const AdminView: Story = {
  args: {
    isAdmin: true,
    initialRepositories: sampleRepositories,
  },
};

export const EmptyState: Story = {
  args: {
    isAdmin: true,
    initialRepositories: [],
  },
};