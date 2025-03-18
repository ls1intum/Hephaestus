import { Meta, StoryObj } from '@storybook/angular';
import { BadPracticeSummaryComponent } from '@app/user/bad-practice-summary/bad-practice-summary.component';

const meta: Meta<BadPracticeSummaryComponent> = {
  component: BadPracticeSummaryComponent,
  tags: ['autodocs'] // Auto-generate docs if enabled
};

export default meta;

type Story = StoryObj<BadPracticeSummaryComponent>;

export const Default: Story = {
  args: {
    summary: 'This pull request has 3 bad practices.'
  }
};
