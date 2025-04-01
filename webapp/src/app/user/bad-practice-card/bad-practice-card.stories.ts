import { Meta, StoryObj } from '@storybook/angular';
import { BadPracticeCardComponent } from './bad-practice-card.component';
import { PullRequestBadPractice } from '@app/core/modules/openapi';

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
    state: PullRequestBadPractice.StateEnum.GoodPractice
  }
};

export const Fixed: Story = {
  args: {
    title: 'Avoid using any type',
    description: 'Using the any type defeats the purpose of TypeScript.',
    state: 'FIXED'
  }
};

export const CriticalIssue: Story = {
  args: {
    title: 'Avoid using any type',
    description: 'Using the any type defeats the purpose of TypeScript.',
    state: 'CRITICAL_ISSUE'
  }
};

export const NormalIssue: Story = {
  args: {
    title: 'Avoid using any type',
    description: 'Using the any type defeats the purpose of TypeScript.',
    state: 'NORMAL_ISSUE'
  }
};

export const MinorIssue: Story = {
  args: {
    title: 'Avoid using any type',
    description: 'Using the any type defeats the purpose of TypeScript.',
    state: 'MINOR_ISSUE'
  }
};

export const WontFix: Story = {
  args: {
    title: 'Avoid using any type',
    description: 'Using the any type defeats the purpose of TypeScript.',
    state: 'WONT_FIX'
  }
};

export const Wrong: Story = {
  args: {
    title: 'Avoid using any type',
    description: 'Using the any type defeats the purpose of TypeScript.',
    state: 'WRONG'
  }
};
