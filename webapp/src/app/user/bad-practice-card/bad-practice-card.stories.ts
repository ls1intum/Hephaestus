import { Meta, StoryObj } from '@storybook/angular';
import { BadPracticeCardComponent } from './bad-practice-card.component';

const meta: Meta<BadPracticeCardComponent> = {
  component: BadPracticeCardComponent,
  tags: ['autodocs'] // Auto-generate docs if enabled
};

export default meta;

type Story = StoryObj<BadPracticeCardComponent>;

export const Default: Story = {
  args: {
    title: 'Avoid using any type',
    description: 'Using the any type defeats the purpose of TypeScript.',
    resolved: false
  }
};
