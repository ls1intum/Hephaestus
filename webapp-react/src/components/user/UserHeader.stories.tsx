import type { Meta, StoryObj } from '@storybook/react';
import { UserHeader } from './UserHeader';

const meta: Meta<typeof UserHeader> = {
  title: 'Components/User/UserHeader',
  component: UserHeader,
  tags: ['autodocs'],
  parameters: {
    layout: 'fullscreen',
  }
};

export default meta;
type Story = StoryObj<typeof UserHeader>;

export const Loading: Story = {
  args: {
    isLoading: true
  }
};

export const WithFullData: Story = {
  args: {
    isLoading: false,
    user: {
      login: 'johndoe',
      name: 'John Doe',
      avatarUrl: 'https://avatars.githubusercontent.com/u/1234567?v=4',
      htmlUrl: 'https://github.com/johndoe'
    },
    firstContribution: '2022-03-15T10:30:00Z',
    contributedRepositories: [
      {
        nameWithOwner: 'ls1intum/Artemis',
        htmlUrl: 'https://github.com/ls1intum/Artemis'
      },
      {
        nameWithOwner: 'ls1intum/Hephaestus',
        htmlUrl: 'https://github.com/ls1intum/Hephaestus'
      },
      {
        nameWithOwner: 'ls1intum/Athena',
        htmlUrl: 'https://github.com/ls1intum/Athena'
      }
    ],
    leaguePoints: 1750
  }
};

export const WithMinimalData: Story = {
  args: {
    isLoading: false,
    user: {
      login: 'janedoe',
      name: 'Jane Doe',
      avatarUrl: 'https://avatars.githubusercontent.com/u/7654321?v=4',
      htmlUrl: 'https://github.com/janedoe'
    },
    leaguePoints: 350
  }
};

export const WithoutRepositories: Story = {
  args: {
    isLoading: false,
    user: {
      login: 'developer',
      name: 'Dev Eloper',
      avatarUrl: 'https://avatars.githubusercontent.com/u/9876543?v=4',
      htmlUrl: 'https://github.com/developer'
    },
    firstContribution: '2021-07-22T14:20:00Z',
    contributedRepositories: [],
    leaguePoints: 875
  }
};