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

const meta: Meta<typeof AboutPage> = {
  component: AboutPage,
};

export default meta;
type Story = StoryObj<typeof AboutPage>;

export const Default: Story = {
  args: {
    isPending: false,
    isError: false,
    otherContributors: mockContributors,
  }
};

export const IsLoading: Story = {
  args: {
    isPending: true,
    isError: false,
    otherContributors: [],
  },
};

export const IsError: Story = {
  args: {
    isPending: false,
    isError: true,
    otherContributors: [],
  },
};