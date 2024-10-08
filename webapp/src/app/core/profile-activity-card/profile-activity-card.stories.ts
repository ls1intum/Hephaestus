import { Meta, StoryObj } from '@storybook/angular';
import { ProfileActivityCardComponent } from './profile-activity-card.component';

const meta: Meta<ProfileActivityCardComponent> = {
  component: ProfileActivityCardComponent,
  tags: ['autodocs'] // Auto-generate docs if enabled
};

export default meta;

type Story = StoryObj<ProfileActivityCardComponent>;

export const Default: Story = {
  args: {
    createdAt: '2024-10-06',
    state: 'CHANGES_REQUESTED',
    repositoryName: 'Hephaestus',
    pullRequestNumber: 100,
    pullRequestState: 'OPEN',
    pullRequestUrl: 'https://github.com/ls1intum/Hephaestus/pull/100'
  }
};
