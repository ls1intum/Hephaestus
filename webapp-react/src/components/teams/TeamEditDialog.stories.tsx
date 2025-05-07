import type { Meta, StoryObj } from '@storybook/react';
import { TeamEditDialog } from './TeamEditDialog';
import type { TeamInfo } from './types';
import { action } from '@storybook/addon-actions';
import { useState } from 'react';

const meta: Meta<typeof TeamEditDialog> = {
  title: 'Components/Teams/TeamEditDialog',
  component: TeamEditDialog,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  argTypes: {
    onSave: { action: 'team saved' },
  }
};

export default meta;
type Story = StoryObj<typeof TeamEditDialog>;

// Sample team data
const sampleTeam: TeamInfo = {
  id: 1,
  name: 'Frontend Team',
  color: '3b82f6', // Remove # prefix from color
  hidden: false,
  repositories: [
    { id: 101, nameWithOwner: 'organization/frontend' },
    { id: 102, nameWithOwner: 'organization/design-system' }
  ],
  labels: [
    { id: 201, name: 'bug', color: 'ef4444', description: 'Something isn\'t working' },
    { id: 202, name: 'enhancement', color: '3b82f6', description: 'New feature or request' }
  ],
  members: [
    { id: 301, login: 'sarah', name: 'Sarah Johnson', avatarUrl: 'https://i.pravatar.cc/150?u=sarah' },
    { id: 302, login: 'michael', name: 'Michael Chen', avatarUrl: 'https://i.pravatar.cc/150?u=michael' }
  ]
};

// Wrapper component to handle dialog state
const DialogWrapper = (args: any) => {
  const [open, setOpen] = useState(true);
  return <TeamEditDialog {...args} open={open} onOpenChange={setOpen} />;
};

export const Default: Story = {
  render: (args) => <DialogWrapper {...args} />,
  args: {
    team: sampleTeam,
    onSave: action('team saved'),
    isSaving: false,
  }
};

export const Saving: Story = {
  render: (args) => <DialogWrapper {...args} />,
  args: {
    team: sampleTeam,
    onSave: action('team saved'),
    isSaving: true,
  }
};