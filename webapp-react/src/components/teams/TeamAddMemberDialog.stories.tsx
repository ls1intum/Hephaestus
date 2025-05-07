import type { Meta, StoryObj } from '@storybook/react';
import { TeamAddMemberDialog } from './TeamAddMemberDialog';
import type { TeamMember } from './types';
import { action } from '@storybook/addon-actions';
import { useState } from 'react';

const meta: Meta<typeof TeamAddMemberDialog> = {
  title: 'Components/Teams/TeamAddMemberDialog',
  component: TeamAddMemberDialog,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  argTypes: {
    onAddMember: { action: 'member added' },
    onSearchUsers: { action: 'search users' },
  }
};

export default meta;
type Story = StoryObj<typeof TeamAddMemberDialog>;

// Sample users
const sampleTeamMembers: TeamMember[] = [
  { id: 301, login: 'sarah', name: 'Sarah Johnson', avatarUrl: 'https://i.pravatar.cc/150?u=sarah' },
  { id: 302, login: 'michael', name: 'Michael Chen', avatarUrl: 'https://i.pravatar.cc/150?u=michael' },
];

const sampleAllUsers: TeamMember[] = [
  ...sampleTeamMembers,
  { id: 303, login: 'emma', name: 'Emma Wilson', avatarUrl: 'https://i.pravatar.cc/150?u=emma' },
  { id: 304, login: 'david', name: 'David Rodriguez', avatarUrl: 'https://i.pravatar.cc/150?u=david' },
  { id: 305, login: 'alex', name: 'Alex Kim', avatarUrl: 'https://i.pravatar.cc/150?u=alex' },
  { id: 306, login: 'jamie', name: 'Jamie Taylor', avatarUrl: 'https://i.pravatar.cc/150?u=jamie' },
];

// Wrapper component to handle dialog state
const DialogWrapper = (args: any) => {
  const [open, setOpen] = useState(true);
  return <TeamAddMemberDialog {...args} open={open} onOpenChange={setOpen} />;
};

export const Default: Story = {
  render: (args) => <DialogWrapper {...args} />,
  args: {
    teamId: 1,
    teamName: 'Frontend Team',
    teamMembers: sampleTeamMembers,
    allUsers: sampleAllUsers,
    onAddMember: action('member added'),
    isSearching: false,
  }
};

export const WithExternalSearch: Story = {
  render: (args) => <DialogWrapper {...args} />,
  args: {
    teamId: 1,
    teamName: 'Frontend Team',
    teamMembers: sampleTeamMembers,
    allUsers: sampleAllUsers,
    onAddMember: action('member added'),
    isSearching: false,
    onSearchUsers: action('search users')
  }
};

export const Loading: Story = {
  render: (args) => <DialogWrapper {...args} />,
  args: {
    teamId: 1,
    teamName: 'Frontend Team',
    teamMembers: sampleTeamMembers,
    allUsers: sampleAllUsers,
    onAddMember: action('member added'),
    isSearching: true,
    onSearchUsers: action('search users')
  }
};