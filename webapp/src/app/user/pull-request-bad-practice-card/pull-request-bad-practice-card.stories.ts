import { Meta, StoryObj } from '@storybook/angular';
import { PullRequestBadPracticeCardComponent } from './pull-request-bad-practice-card.component';

const meta: Meta<PullRequestBadPracticeCardComponent> = {
  component: PullRequestBadPracticeCardComponent,
  tags: ['autodocs'] // Auto-generate docs if enabled
};

export default meta;

type Story = StoryObj<PullRequestBadPracticeCardComponent>;

export const Default: Story = {
  args: {
    title: 'Add feature X',
    number: 12,
    additions: 10,
    deletions: 5,
    htmlUrl: 'http://example.com',
    state: 'OPEN',
    isDraft: false,
    isMerged: false,
    repositoryName: 'Artemis',
    createdAt: '2024-01-01',
    pullRequestLabels: [
      { id: 1, name: 'bug', color: 'f00000' },
      { id: 2, name: 'enhancement', color: '008000' }
    ],
    badPractices: [
      {
        title: 'Avoid using any type',
        description: 'Using the any type defeats the purpose of TypeScript.'
      },
      {
        title: 'Unchecked checkbox in description',
        description: 'Unchecked checkboxes in the description are not allowed.'
      }
    ],
    badPracticeSummary: 'We found 2 bad practices in this pull request. Please fix them. Thank you!'
  }
};

export const isLoading: Story = {
  args: {
    title: 'Add feature X',
    number: 12,
    additions: 10,
    deletions: 5,
    htmlUrl: 'http://example.com',
    state: 'OPEN',
    isDraft: false,
    isMerged: false,
    repositoryName: 'Artemis',
    createdAt: '2024-01-01',
    pullRequestLabels: [
      { id: 1, name: 'bug', color: 'f00000' },
      { id: 2, name: 'enhancement', color: '008000' }
    ],
    badPractices: [
      {
        title: 'Avoid using any type',
        description: 'Using the any type defeats the purpose of TypeScript.'
      },
      {
        title: 'Unchecked checkbox in description',
        description: 'Unchecked checkboxes in the description are not allowed.'
      }
    ],
    isLoading: true
  }
};
