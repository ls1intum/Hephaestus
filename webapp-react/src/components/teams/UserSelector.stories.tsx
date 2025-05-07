import type { Meta, StoryObj } from '@storybook/react';
import { UserSelector } from './UserSelector';
import { action } from '@storybook/addon-actions';

const meta: Meta<typeof UserSelector> = {
  title: 'Components/Teams/UserSelector',
  component: UserSelector,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  argTypes: {
    onSelect: { action: 'user selected' },
    onSearch: { action: 'search executed' },
  }
};

export default meta;
type Story = StoryObj<typeof UserSelector>;

// Sample user data
const sampleUsers = [
  { id: 301, login: 'sarah', name: 'Sarah Johnson', avatarUrl: 'https://i.pravatar.cc/150?u=sarah' },
  { id: 302, login: 'michael', name: 'Michael Chen', avatarUrl: 'https://i.pravatar.cc/150?u=michael' },
  { id: 303, login: 'emma', name: 'Emma Wilson', avatarUrl: 'https://i.pravatar.cc/150?u=emma' },
  { id: 304, login: 'david', name: 'David Rodriguez', avatarUrl: 'https://i.pravatar.cc/150?u=david' },
  { id: 305, login: 'alex', name: 'Alex Kim', avatarUrl: 'https://i.pravatar.cc/150?u=alex' },
  { id: 306, login: 'jamie', name: 'Jamie Taylor', avatarUrl: 'https://i.pravatar.cc/150?u=jamie' },
  { id: 307, login: 'chris', name: 'Chris Patel', avatarUrl: 'https://i.pravatar.cc/150?u=chris' },
  { id: 308, login: 'pat', name: 'Pat Thompson', avatarUrl: 'https://i.pravatar.cc/150?u=pat' }
];

export const Default: Story = {
  args: {
    users: sampleUsers,
    selectedUserIds: [],
    onSelect: action('user selected'),
    onSearch: action('search executed'),
    isLoading: false,
    disabled: false,
    placeholder: 'Select a user',
    canSearchExternal: false
  }
};

export const WithSelectedUsers: Story = {
  args: {
    users: sampleUsers,
    selectedUserIds: [301, 304, 308],
    onSelect: action('user selected'),
    onSearch: action('search executed'),
    isLoading: false,
    disabled: false,
    placeholder: 'Select a user',
    canSearchExternal: false
  }
};

export const WithExternalSearch: Story = {
  args: {
    users: sampleUsers,
    selectedUserIds: [],
    onSelect: action('user selected'),
    onSearch: action('search executed'),
    isLoading: false,
    disabled: false,
    placeholder: 'Search GitHub users...',
    canSearchExternal: true
  }
};

export const Loading: Story = {
  args: {
    users: [],
    selectedUserIds: [],
    onSelect: action('user selected'),
    onSearch: action('search executed'),
    isLoading: true,
    disabled: false,
    placeholder: 'Select a user',
    canSearchExternal: true
  }
};

export const Disabled: Story = {
  args: {
    users: sampleUsers,
    selectedUserIds: [],
    onSelect: action('user selected'),
    isLoading: false,
    disabled: true,
    placeholder: 'Select a user'
  }
};