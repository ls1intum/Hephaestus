import { Meta, StoryObj } from '@storybook/angular';
import { IssueCardComponent } from './issue-card.component';
import dayjs from 'dayjs';

const meta: Meta<IssueCardComponent> = {
  title: 'Components/Core/IssueCard',
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
    createdAt: dayjs('Jan 1'),
    pullRequestLabels: [
      { name: 'bug', color: 'red' },
      { name: 'enhancement', color: 'green' }
    ],
    reviews: [
      {
        state: 'APPROVED',
        updatedAt: 'Jan 2'
      },
      {
        state: 'CHANGES_REQUESTED',
        updatedAt: 'Jan 4'
      }
    ]
  }
};
