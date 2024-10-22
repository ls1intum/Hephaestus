import { Meta, StoryObj } from '@storybook/angular';
import { IssueCardComponent } from './issue-card.component';

const meta: Meta<IssueCardComponent> = {
  component: IssueCardComponent,
  tags: ['autodocs'] // Auto-generate docs if enabled
};

export default meta;

type Story = StoryObj<IssueCardComponent>;

export const Default: Story = {
  args: {
    title: 'Add feature X',
    number: 12,
    additions: 10,
    deletions: 5,
    url: 'http://example.com',
    state: 'OPEN',
    repositoryName: 'Artemis',
    createdAt: '2024-01-01',
    pullRequestLabels: new Set([
      { name: 'bug', color: 'f00000' },
      { name: 'enhancement', color: '008000' }
    ])
  }
};

export const isLoading: Story = {
  args: {
    title: 'Add feature X',
    number: 12,
    additions: 10,
    deletions: 5,
    url: 'http://example.com',
    state: 'OPEN',
    repositoryName: 'Artemis',
    createdAt: '2024-01-01',
    pullRequestLabels: new Set([
      { name: 'bug', color: 'f00000' },
      { name: 'enhancement', color: '008000' }
    ]),
    isLoading: true
  }
};