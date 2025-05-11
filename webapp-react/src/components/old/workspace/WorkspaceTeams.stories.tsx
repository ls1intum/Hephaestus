import type { Meta, StoryObj } from '@storybook/react';
import { WorkspaceTeams } from './WorkspaceTeams';

const meta: Meta<typeof WorkspaceTeams> = {
  title: 'Workspace/Teams',
  component: WorkspaceTeams,
  parameters: {
    layout: 'padded',
  },
};

export default meta;
type Story = StoryObj<typeof WorkspaceTeams>;

const sampleTeams = [
  { 
    id: 1, 
    name: 'Frontend Team', 
    color: '3b82f6',
    membersCount: 8,
    repositoriesCount: 4,
    labelsCount: 12,
    hidden: false,
    members: [
      { id: 1, login: 'johndoe', name: 'John Doe', avatarUrl: 'https://github.com/identicons/app/oauth_app/1' },
      { id: 2, login: 'janedoe', name: 'Jane Doe', avatarUrl: 'https://github.com/identicons/app/oauth_app/2' },
      { id: 3, login: 'bobsmith', name: 'Bob Smith', avatarUrl: 'https://github.com/identicons/app/oauth_app/3' },
      { id: 4, login: 'alicewhite', name: 'Alice White', avatarUrl: 'https://github.com/identicons/app/oauth_app/4' }
    ]
  },
  { 
    id: 2, 
    name: 'Backend Team', 
    color: '10b981',
    membersCount: 6,
    repositoriesCount: 3,
    labelsCount: 8,
    hidden: false,
    members: [
      { id: 5, login: 'michaelb', name: 'Michael Brown', avatarUrl: 'https://github.com/identicons/app/oauth_app/5' },
      { id: 6, login: 'sarahj', name: 'Sarah Johnson', avatarUrl: 'https://github.com/identicons/app/oauth_app/6' },
      { id: 7, login: 'davidm', name: 'David Miller', avatarUrl: 'https://github.com/identicons/app/oauth_app/7' }
    ]
  },
  { 
    id: 3, 
    name: 'DevOps Team', 
    color: 'd946ef',
    membersCount: 4,
    repositoriesCount: 5,
    labelsCount: 6,
    hidden: true,
    members: [
      { id: 8, login: 'emilyr', name: 'Emily Robinson', avatarUrl: 'https://github.com/identicons/app/oauth_app/8' },
      { id: 9, login: 'ryank', name: 'Ryan Kim', avatarUrl: 'https://github.com/identicons/app/oauth_app/9' }
    ]
  },
  { 
    id: 4, 
    name: 'Design Team', 
    color: 'f59e0b',
    membersCount: 5,
    repositoriesCount: 2,
    labelsCount: 4,
    hidden: false,
    members: [
      { id: 10, login: 'olivias', name: 'Olivia Smith', avatarUrl: 'https://github.com/identicons/app/oauth_app/10' },
      { id: 11, login: 'noahj', name: 'Noah Johnson', avatarUrl: 'https://github.com/identicons/app/oauth_app/11' },
      { id: 12, login: 'sophiaw', name: 'Sophia Williams', avatarUrl: 'https://github.com/identicons/app/oauth_app/12' }
    ]
  }
];

export const Default: Story = {
  args: {
    isAdmin: false,
    initialTeams: sampleTeams,
  },
};

export const AdminView: Story = {
  args: {
    isAdmin: true,
    initialTeams: sampleTeams,
  },
};

export const EmptyState: Story = {
  args: {
    isAdmin: true,
    initialTeams: [],
  },
};