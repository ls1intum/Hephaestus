import type { Meta, StoryObj } from '@storybook/react';
import { LabelSelector } from './LabelSelector';
import { action } from '@storybook/addon-actions';

const meta: Meta<typeof LabelSelector> = {
  title: 'Components/Teams/LabelSelector',
  component: LabelSelector,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  argTypes: {
    onSelect: { action: 'label selected' },
  }
};

export default meta;
type Story = StoryObj<typeof LabelSelector>;

// Sample label data
const sampleLabels = [
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
  { 
    id: 206, 
    name: 'high priority', 
    color: 'f59e0b', 
    description: 'Needs urgent attention',
    repository: { id: 104, nameWithOwner: 'organization/api' }
  }
];

export const Default: Story = {
  args: {
    labels: sampleLabels,
    selectedLabelIds: [],
    onSelect: action('label selected'),
    isLoading: false,
    disabled: false,
    placeholder: 'Select a label'
  }
};

export const WithSelectedLabels: Story = {
  args: {
    labels: sampleLabels,
    selectedLabelIds: [201, 203],
    onSelect: action('label selected'),
    isLoading: false,
    disabled: false,
    placeholder: 'Select a label'
  }
};

export const Loading: Story = {
  args: {
    labels: [],
    selectedLabelIds: [],
    onSelect: action('label selected'),
    isLoading: true,
    disabled: false
  }
};

export const Disabled: Story = {
  args: {
    labels: sampleLabels,
    selectedLabelIds: [],
    onSelect: action('label selected'),
    isLoading: false,
    disabled: true
  }
};