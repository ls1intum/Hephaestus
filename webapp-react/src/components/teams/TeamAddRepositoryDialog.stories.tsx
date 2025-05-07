import type { Meta, StoryObj } from '@storybook/react';
import { TeamAddRepositoryDialog } from './TeamAddRepositoryDialog';
import type { RepositoryInfo } from './types';
import { action } from '@storybook/addon-actions';
import { useState } from 'react';

const meta: Meta<typeof TeamAddRepositoryDialog> = {
  title: 'Components/Teams/TeamAddRepositoryDialog',
  component: TeamAddRepositoryDialog,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  argTypes: {
    onAddRepository: { action: 'repository added' },
  }
};

export default meta;
type Story = StoryObj<typeof TeamAddRepositoryDialog>;

// Sample repositories
const sampleTeamRepositories: RepositoryInfo[] = [
  { id: 101, nameWithOwner: 'organization/frontend' },
  { id: 102, nameWithOwner: 'organization/design-system' },
];

const sampleAllRepositories: RepositoryInfo[] = [
  ...sampleTeamRepositories,
  { id: 103, nameWithOwner: 'organization/api' },
  { id: 104, nameWithOwner: 'organization/docs' },
  { id: 105, nameWithOwner: 'organization/server' },
  { id: 106, nameWithOwner: 'organization/utilities' },
  { id: 107, nameWithOwner: 'organization/mobile-app' },
];

// Wrapper component to handle dialog state
const DialogWrapper = (args: any) => {
  const [open, setOpen] = useState(true);
  return <TeamAddRepositoryDialog {...args} open={open} onOpenChange={setOpen} />;
};

export const Default: Story = {
  render: (args) => <DialogWrapper {...args} />,
  args: {
    teamId: 1,
    teamName: 'Frontend Team',
    teamRepositories: sampleTeamRepositories,
    repositories: sampleAllRepositories,
    onAddRepository: action('repository added'),
    isLoading: false,
  }
};

export const Loading: Story = {
  render: (args) => <DialogWrapper {...args} />,
  args: {
    teamId: 1,
    teamName: 'Frontend Team',
    teamRepositories: sampleTeamRepositories,
    repositories: [],
    onAddRepository: action('repository added'),
    isLoading: true,
  }
};

export const NoAvailableRepositories: Story = {
  render: (args) => <DialogWrapper {...args} />,
  args: {
    teamId: 1,
    teamName: 'Frontend Team',
    teamRepositories: sampleAllRepositories,
    repositories: sampleAllRepositories,
    onAddRepository: action('repository added'),
    isLoading: false,
  }
};