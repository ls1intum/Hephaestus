import type { Meta, StoryObj } from '@storybook/react';
import { WorkspaceUsers } from './WorkspaceUsers';
import type { User } from './types';

const meta = {
  title: 'Workspace/WorkspaceUsers',
  component: WorkspaceUsers,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
} satisfies Meta<typeof WorkspaceUsers>;

export default meta;
type Story = StoryObj<typeof meta>;

const mockUsers: User[] = [
  {
    id: 1,
    login: 'johndoe',
    name: 'John Doe',
    avatarUrl: 'https://github.com/identicons/app/oauth_app/1',
    role: 'Admin',
    isActive: true,
  },
  {
    id: 2,
    login: 'janedoe',
    name: 'Jane Doe',
    avatarUrl: 'https://github.com/identicons/app/oauth_app/2',
    role: 'Developer',
    isActive: true,
  },
  {
    id: 3,
    login: 'bobsmith',
    name: 'Bob Smith',
    avatarUrl: 'https://github.com/identicons/app/oauth_app/3',
    role: 'Viewer',
    isActive: false,
  },
];

export const AdminView: Story = {
  args: {
    isAdmin: true,
    initialUsers: mockUsers,
  },
};

export const UserView: Story = {
  args: {
    isAdmin: false,
    initialUsers: mockUsers,
  },
};

export const EmptyState: Story = {
  args: {
    isAdmin: true,
    initialUsers: [],
  },
};

export const EmptyStateNonAdmin: Story = {
  args: {
    isAdmin: false,
    initialUsers: [],
  },
};