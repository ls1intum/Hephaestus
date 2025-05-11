import type { Meta, StoryObj } from '@storybook/react';
import { TeamForm } from './TeamForm';
import { action } from '@storybook/addon-actions';

const meta: Meta<typeof TeamForm> = {
  title: 'Components/Teams/TeamForm',
  component: TeamForm,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  argTypes: {
    onSubmit: { action: 'submitted' },
    onCancel: { action: 'cancelled' },
  }
};

export default meta;
type Story = StoryObj<typeof TeamForm>;

export const CreateNew: Story = {
  args: {
    team: undefined,
    onSubmit: action('submitted'),
    onCancel: action('cancelled'),
    isSubmitting: false
  }
};

export const EditExisting: Story = {
  args: {
    team: {
      id: 1,
      name: 'Frontend Team',
      color: '3b82f6',
      hidden: false,
      repositories: [],
      labels: [],
      members: []
    },
    onSubmit: action('submitted'),
    onCancel: action('cancelled'),
    isSubmitting: false
  }
};

export const Submitting: Story = {
  args: {
    team: {
      name: 'New Team',
      color: 'f59e0b',
      hidden: true
    },
    onSubmit: action('submitted'),
    onCancel: action('cancelled'),
    isSubmitting: true
  }
};