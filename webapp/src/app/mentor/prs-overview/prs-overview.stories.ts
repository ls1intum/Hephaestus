import { type Meta, type StoryObj } from '@storybook/angular';
import { PrsOverviewComponent } from './prs-overview.component';

const meta: Meta<PrsOverviewComponent> = {
  component: PrsOverviewComponent,
  tags: ['autodocs'],
  args: {
    pullRequests: [
      { number: 220, title: 'AI Mentor memory integration', state: 'OPEN', isDraft: false, isMerged: false, url: 'https://github.com/' },
      { number: 218, title: 'Update to Angular v19', state: '', isDraft: true, isMerged: false, url: 'https://github.com/' },
      { number: 209, title: 'Fix: Persist User Change on Navigation', state: '', isDraft: false, isMerged: true, url: 'https://github.com/' }
    ]
  }
};

export default meta;
type Story = StoryObj<PrsOverviewComponent>;

export const Default: Story = {};
