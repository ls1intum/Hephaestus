import type { Meta, StoryObj } from '@storybook/react';
import { Workspace } from './Workspace';
import { WorkspaceUsers } from './WorkspaceUsers';
import { WorkspaceRepositories } from './WorkspaceRepositories';
import { WorkspaceTeams } from './WorkspaceTeams';
import { createContext } from 'react';

// Create context for container components
export const WorkspaceContext = createContext({
  WorkspaceUsers,
  WorkspaceRepositories,
  WorkspaceTeams,
});

const meta: Meta<typeof Workspace> = {
  title: 'Pages/Workspace',
  component: Workspace,
  parameters: {
    layout: 'fullscreen',
  },
};

export default meta;
type Story = StoryObj<typeof Workspace>;

export const Default: Story = {
  args: {
    organizationName: 'Acme Corp',
    userCount: 42,
    repositoryCount: 23,
    teamCount: 7,
    isAdmin: false,
  },
};

export const AdminView: Story = {
  args: {
    organizationName: 'Acme Corp',
    userCount: 42,
    repositoryCount: 23,
    teamCount: 7,
    isAdmin: true,
  },
};

export const EmptyOrganization: Story = {
  args: {
    organizationName: 'New Company',
    userCount: 0,
    repositoryCount: 0,
    teamCount: 0,
    isAdmin: true,
  },
};