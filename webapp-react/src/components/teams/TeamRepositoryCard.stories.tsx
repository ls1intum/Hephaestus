import type { Meta, StoryObj } from '@storybook/react';
import { TeamRepositoryCard } from './TeamRepositoryCard';
import type { RepositoryInfo } from './types';

// Define TeamRepository as an alias for RepositoryInfo
type TeamRepository = RepositoryInfo;

const meta: Meta<typeof TeamRepositoryCard> = {
  title: 'Components/Teams/TeamRepositoryCard',
  component: TeamRepositoryCard,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  argTypes: {
    onRemove: { action: 'repository removed' },
  }
};

export default meta;
type Story = StoryObj<typeof TeamRepositoryCard>;

// Sample repository data
const sampleRepo: TeamRepository = {
  id: 101,
  nameWithOwner: 'organization/frontend',
  description: 'Frontend application built with React',
  url: 'https://github.com/organization/frontend'
};

export const Default: Story = {
  args: {
    repository: sampleRepo,
    onRemove: undefined,
  }
};

export const WithRemove: Story = {
  args: {
    repository: sampleRepo,
    onRemove: (id) => console.log(`Remove repository: ${id}`),
  }
};

export const WithoutDescription: Story = {
  args: {
    repository: { 
      ...sampleRepo,
      description: undefined
    },
    onRemove: (id) => console.log(`Remove repository: ${id}`),
  }
};

export const LongRepositoryName: Story = {
  args: {
    repository: { 
      ...sampleRepo,
      nameWithOwner: 'very-long-organization-name/extremely-long-repository-name-that-should-be-truncated'
    },
    onRemove: (id) => console.log(`Remove repository: ${id}`),
  }
};

export const IsRemoving: Story = {
  args: {
    repository: sampleRepo,
    onRemove: (id) => console.log(`Remove repository: ${id}`),
    isRemoving: true,
  }
};