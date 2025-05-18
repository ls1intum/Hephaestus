import type { Meta, StoryObj } from '@storybook/react';
import { AboutPage } from './AboutPage';

// Mock data for the stories
const mockContributors = [
  {
    id: 1,
    login: 'contributor1',
    name: 'Alex Smith',
    avatarUrl: 'https://i.pravatar.cc/150?img=1',
    htmlUrl: 'https://github.com/contributor1'
  },
  {
    id: 2,
    login: 'contributor2',
    name: 'Jamie Lee',
    avatarUrl: 'https://i.pravatar.cc/150?img=2',
    htmlUrl: 'https://github.com/contributor2'
  },
  {
    id: 3,
    login: 'contributor3',
    name: 'Sam Wilson',
    avatarUrl: 'https://i.pravatar.cc/150?img=3',
    htmlUrl: 'https://github.com/contributor3'
  },
  {
    id: 4,
    login: 'contributor4',
    name: 'Taylor Kim',
    avatarUrl: 'https://i.pravatar.cc/150?img=4',
    htmlUrl: 'https://github.com/contributor4'
  },
  {
    id: 5,
    login: 'contributor5',
    name: 'Jordan Chen',
    avatarUrl: 'https://i.pravatar.cc/150?img=5',
    htmlUrl: 'https://github.com/contributor5'
  },
  {
    id: 6,
    login: 'contributor6',
    name: 'Casey Wong',
    avatarUrl: 'https://i.pravatar.cc/150?img=6',
    htmlUrl: 'https://github.com/contributor6'
  },
  {
    id: 7,
    login: 'contributor7',
    name: 'Erin Parker',
    avatarUrl: 'https://i.pravatar.cc/150?img=7',
    htmlUrl: 'https://github.com/contributor7'
  },
  {
    id: 8,
    login: 'contributor8',
    name: 'Morgan Davis',
    avatarUrl: 'https://i.pravatar.cc/150?img=8',
    htmlUrl: 'https://github.com/contributor8'
  }
];

/**
 * About page component that showcases the Hephaestus project, highlighting its mission and team.
 * Features a clean hero section, focused feature cards, engaging testimonials, and team information.
 * Gracefully handles loading and error states with appropriate visual feedback.
 */
const meta = {
  component: AboutPage,
  tags: ['autodocs'],
  parameters: {
    layout: "padded",
    docs: {
      description: {
        component: 'A clean, focused page that highlights the Hephaestus project mission, its core features, and showcases the team behind it.',
      },
    },
  },
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
 * The showcase view displays the full About page with mission statement, features, 
 * testimonials, and a complete list of contributors.
 */
export const Showcase: Story = {
  args: {
    isPending: false,
    isError: false,
    otherContributors: mockContributors,
  }
};

/**
 * Loading state when contributor data is being fetched.
 * Shows skeleton placeholders while the data loads.
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
 * Displays a user-friendly error message with explanation.
 */
export const IsError: Story = {
  args: {
    isPending: false,
    isError: true,
    otherContributors: [],
  },
};

/**
 * View when there are no contributors yet.
 * Shows an encouraging message to be the first contributor.
 */
export const NoContributors: Story = {
  args: {
    isPending: false,
    isError: false,
    otherContributors: [],
  },
};