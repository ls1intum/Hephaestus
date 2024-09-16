import { Meta, StoryObj } from '@storybook/angular';
import { PullRequestWidgetComponent } from './pull-request-widget.component';
import { PullRequest, Repository } from '@app/core/modules/openapi';

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

const pullRequest: PullRequest = {
  title: 'Add feature X',
  additions: 10,
  deletions: 5,
  url: 'http://example.com',
  state: 'OPEN',
  repository: repo,
  createdAt: 'Jan 1',
  id: 12
};

export const Default: Story = {
  args: { pullRequest }
};
