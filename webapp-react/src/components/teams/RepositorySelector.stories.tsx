import type { Meta, StoryObj } from '@storybook/react';
import { RepositorySelector } from './RepositorySelector';
import { action } from '@storybook/addon-actions';

const meta: Meta<typeof RepositorySelector> = {
  title: 'Components/Teams/RepositorySelector',
  component: RepositorySelector,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  argTypes: {
    onSelect: { action: 'repository selected' },
  }
};

export default meta;
type Story = StoryObj<typeof RepositorySelector>;

// Sample repository data
const sampleRepositories = [
  { id: 101, nameWithOwner: 'organization/frontend' },
  { id: 102, nameWithOwner: 'organization/backend' },
  { id: 103, nameWithOwner: 'organization/api' },
  { id: 104, nameWithOwner: 'organization/design-system' },
  { id: 105, nameWithOwner: 'organization/docs' },
  { id: 106, nameWithOwner: 'organization/utilities' },
  { id: 107, nameWithOwner: 'organization/mobile-app' },
  { id: 108, nameWithOwner: 'organization/infrastructure' },
  { id: 109, nameWithOwner: 'organization/analytics' },
  { id: 110, nameWithOwner: 'organization/testing' },
];

export const Default: Story = {
  args: {
    repositories: sampleRepositories,
    selectedRepositoryIds: [],
    onSelect: action('repository selected'),
    isLoading: false,
    disabled: false,
    placeholder: 'Select a repository'
  }
};

export const WithSelectedRepositories: Story = {
  args: {
    repositories: sampleRepositories,
    selectedRepositoryIds: [101, 103, 105],
    onSelect: action('repository selected'),
    isLoading: false,
    disabled: false,
    placeholder: 'Select a repository'
  }
};

export const Loading: Story = {
  args: {
    repositories: [],
    selectedRepositoryIds: [],
    onSelect: action('repository selected'),
    isLoading: true,
    disabled: false
  }
};

export const Disabled: Story = {
  args: {
    repositories: sampleRepositories,
    selectedRepositoryIds: [],
    onSelect: action('repository selected'),
    isLoading: false,
    disabled: true
  }
};