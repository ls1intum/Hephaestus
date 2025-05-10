import type { Meta, StoryObj } from '@storybook/react';
import { AboutPage } from './AboutPage';

// Mock data for the story
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

const mockProjectManager = {
  id: 5898705,
  login: 'felixdietrich',
  avatarUrl: 'https://i.pravatar.cc/150?img=4',
  htmlUrl: 'https://github.com/felixdietrich'
};

const meta: Meta<typeof AboutPage> = {
  title: 'Features/Info/About/AboutPage',
  component: AboutPage,
  parameters: {
    layout: 'fullscreen',
  },
};

export default meta;
type Story = StoryObj<typeof AboutPage>;

export const Default: Story = {
  args: {
    isPending: false,
    isError: false,
    projectManager: mockProjectManager,
    otherContributors: mockContributors
  }
};

export const Loading: Story = {
  args: {
    isPending: true,
    isError: false,
    projectManager: undefined,
    otherContributors: []
  }
};

export const Error: Story = {
  args: {
    isPending: false,
    isError: true,
    projectManager: undefined,
    otherContributors: []
  }
};