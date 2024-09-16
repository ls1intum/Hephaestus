import { Meta, StoryObj } from '@storybook/angular';
import { PullRequestWidgetComponent } from './pull-request-widget.component';
import { PullRequest, PullRequestReview, Repository } from '@app/core/modules/openapi';

const meta: Meta<PullRequestWidgetComponent> = {
  title: 'Components/PullRequestCard',
  component: PullRequestWidgetComponent,
  tags: ['autodocs'] // Auto-generate docs if enabled
};

export default meta;

type Story = StoryObj<PullRequestWidgetComponent>;

const repo: Repository = {
  name: 'Artemis',
  nameWithOwner: 'artemis-education/artemis',
  defaultBranch: 'master',
  visibility: 'PUBLIC',
  url: 'http://example.com'
};

const reviews = new Set<PullRequestReview>([
  {
    state: 'APPROVED'
  },
  {
    state: 'CHANGES_REQUESTED'
  }
]);

const pullRequest: PullRequest = {
  title: 'Add feature X',
  number: 12,
  additions: 10,
  deletions: 5,
  url: 'http://example.com',
  state: 'OPEN',
  repository: repo,
  createdAt: 'Jan 1',
  pullRequestLabels: new Set([
    { name: 'bug', color: 'red' },
    { name: 'enhancement', color: 'green' }
  ]),
  reviews: reviews
};

export const Default: Story = {
  args: { pullRequest }
};
