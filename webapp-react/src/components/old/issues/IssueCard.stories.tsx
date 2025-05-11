import type { Meta, StoryObj } from '@storybook/react';
import { IssueCard } from './IssueCard';

const meta: Meta<typeof IssueCard> = {
  title: 'Components/Issues/IssueCard',
  component: IssueCard,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  }
};

export default meta;
type Story = StoryObj<typeof IssueCard>;

export const Loading: Story = {
  args: {
    isLoading: true
  }
};

export const OpenPullRequest: Story = {
  args: {
    title: 'Add new feature for user profiles',
    number: 123,
    additions: 256,
    deletions: 18,
    htmlUrl: '#',
    repositoryName: 'webapp-react',
    createdAt: '2023-05-15T09:30:00Z',
    state: 'OPEN',
    isDraft: false,
    isMerged: false,
    pullRequestLabels: [
      {
        name: 'enhancement',
        color: 'a2eeef',
        description: 'New feature or request'
      },
      {
        name: 'frontend',
        color: '0052cc',
        description: 'Frontend related changes'
      }
    ]
  }
};

export const DraftPullRequest: Story = {
  args: {
    title: 'WIP: Refactor authentication system',
    number: 124,
    additions: 305,
    deletions: 217,
    htmlUrl: '#',
    repositoryName: 'webapp-react',
    createdAt: '2023-05-16T13:45:00Z',
    state: 'OPEN',
    isDraft: true,
    isMerged: false,
    pullRequestLabels: [
      {
        name: 'refactor',
        color: 'fbca04',
        description: 'Code refactoring'
      },
      {
        name: 'WIP',
        color: 'bfd4f2',
        description: 'Work in progress'
      }
    ]
  }
};

export const MergedPullRequest: Story = {
  args: {
    title: 'Fix bug in sign-up flow with `async` validation',
    number: 118,
    additions: 42,
    deletions: 15,
    htmlUrl: '#',
    repositoryName: 'webapp-react',
    createdAt: '2023-05-10T10:12:00Z',
    state: 'CLOSED',
    isDraft: false,
    isMerged: true,
    pullRequestLabels: [
      {
        name: 'bug',
        color: 'd73a4a',
        description: 'Something isn\'t working'
      },
      {
        name: 'priority: high',
        color: 'ff0000'
      }
    ]
  }
};

export const ClosedPullRequest: Story = {
  args: {
    title: 'Experimental design system update',
    number: 120,
    additions: 530,
    deletions: 210,
    htmlUrl: '#',
    repositoryName: 'webapp-react',
    createdAt: '2023-05-12T16:30:00Z',
    state: 'CLOSED',
    isDraft: false,
    isMerged: false,
    pullRequestLabels: [
      {
        name: 'design',
        color: '5319e7',
        description: 'Design system changes'
      },
      {
        name: 'wontfix',
        color: 'e4e669',
        description: 'This will not be worked on'
      }
    ]
  }
};