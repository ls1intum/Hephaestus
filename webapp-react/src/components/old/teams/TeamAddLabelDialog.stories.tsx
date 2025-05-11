import type { Meta, StoryObj } from '@storybook/react';
import { TeamAddLabelDialog } from './TeamAddLabelDialog';
import type { LabelInfo } from './types';
import { action } from '@storybook/addon-actions';
import { useState } from 'react';

const meta: Meta<typeof TeamAddLabelDialog> = {
  title: 'Components/Teams/TeamAddLabelDialog',
  component: TeamAddLabelDialog,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  argTypes: {
    onAddLabel: { action: 'label added' },
  }
};

export default meta;
type Story = StoryObj<typeof TeamAddLabelDialog>;

// Sample labels
const sampleTeamLabels: LabelInfo[] = [
  { 
    id: 201, 
    name: 'bug', 
    color: 'ef4444', 
    description: 'Something isn\'t working',
    repository: { id: 101, nameWithOwner: 'organization/frontend' }
  },
  { 
    id: 202, 
    name: 'enhancement', 
    color: '3b82f6', 
    description: 'New feature or request',
    repository: { id: 101, nameWithOwner: 'organization/frontend' }
  },
];

const sampleAllLabels: LabelInfo[] = [
  ...sampleTeamLabels,
  { 
    id: 203, 
    name: 'documentation', 
    color: '10b981', 
    description: 'Improvements or additions to documentation',
    repository: { id: 102, nameWithOwner: 'organization/docs' }
  },
  { 
    id: 204, 
    name: 'good first issue', 
    color: '8b5cf6', 
    description: 'Good for newcomers',
    repository: { id: 101, nameWithOwner: 'organization/frontend' }
  },
  { 
    id: 205, 
    name: 'help wanted', 
    color: 'd946ef', 
    description: 'Extra attention is needed',
    repository: { id: 103, nameWithOwner: 'organization/backend' }
  },
];

// Wrapper component to handle dialog state
const DialogWrapper = (args: any) => {
  const [open, setOpen] = useState(true);
  return <TeamAddLabelDialog {...args} open={open} onOpenChange={setOpen} />;
};

export const Default: Story = {
  render: (args) => <DialogWrapper {...args} />,
  args: {
    teamId: 1,
    teamName: 'Frontend Team',
    teamLabels: sampleTeamLabels,
    labels: sampleAllLabels,
    onAddLabel: action('label added'),
    isLoading: false,
  }
};

export const Loading: Story = {
  render: (args) => <DialogWrapper {...args} />,
  args: {
    teamId: 1,
    teamName: 'Frontend Team',
    teamLabels: sampleTeamLabels,
    labels: [],
    onAddLabel: action('label added'),
    isLoading: true,
  }
};

export const NoAvailableLabels: Story = {
  render: (args) => <DialogWrapper {...args} />,
  args: {
    teamId: 1,
    teamName: 'Frontend Team',
    teamLabels: sampleAllLabels,
    labels: sampleAllLabels,
    onAddLabel: action('label added'),
    isLoading: false,
  }
};