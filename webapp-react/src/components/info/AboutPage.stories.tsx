import type { Meta, StoryObj } from '@storybook/react';
import { AboutPage } from './AboutPage';

// Mock data for the stories
const mockContributors = [
  {
    id: 1,
    login: 'contributor1',
    avatarUrl: 'https://i.pravatar.cc/150?img=1',
    htmlUrl: 'https://github.com/contributor1'
  },
  {
    id: 2,
    login: 'contributor2',
    avatarUrl: 'https://i.pravatar.cc/150?img=2',
    htmlUrl: 'https://github.com/contributor2'
  },
  {
    id: 3,
    login: 'contributor3',
    avatarUrl: 'https://i.pravatar.cc/150?img=3',
    htmlUrl: 'https://github.com/contributor3'
  }
];

/**
 * About page component that displays project information, team lead, and contributors.
 * Shows different states based on loading and error conditions.
 */
const meta = {
  component: AboutPage,
  tags: ['autodocs'],
  argTypes: {
    isPending: {
      description: 'Indicates if contributors data is being loaded',
      control: 'boolean',
    },
    isError: {
      description: 'Indicates if there was an error loading contributors',
      control: 'boolean',
    },
    error: {
      description: 'Optional error object when loading fails',
      control: 'object',
    },
    otherContributors: {
      description: 'List of project contributors to display',
      control: 'object',
    },
  },
} satisfies Meta<typeof AboutPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default view showing the project information and contributors list.
 */
export const Default: Story = {
  args: {
    isPending: false,
    isError: false,
    otherContributors: mockContributors,
  }
};

/**
 * Loading state when contributor data is being fetched.
 */
export const IsLoading: Story = {
  args: {
    isPending: true,
    isError: false,
    otherContributors: [],
  },
};

/**
 * Error state when contributor data fetching fails.
 */
export const IsError: Story = {
  args: {
    isPending: false,
    isError: true,
    otherContributors: [],
  },
};