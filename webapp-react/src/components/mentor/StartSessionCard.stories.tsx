import type { Meta, StoryObj } from '@storybook/react';
import { StartSessionCard } from './StartSessionCard';
import { action } from '@storybook/addon-actions';

const meta: Meta<typeof StartSessionCard> = {
  title: 'Components/Mentor/StartSessionCard',
  component: StartSessionCard,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  argTypes: {
    onCreateNewSession: { action: 'new session created' },
  }
};

export default meta;
type Story = StoryObj<typeof StartSessionCard>;

export const Loading: Story = {
  args: {
    isLoading: true,
    onCreateNewSession: action('new session created')
  }
};

export const FirstSession: Story = {
  args: {
    isLoading: false,
    hasSessions: false,
    isLastSessionClosed: true,
    onCreateNewSession: action('new session created')
  }
};

export const WithExistingSessions: Story = {
  args: {
    isLoading: false,
    hasSessions: true,
    isLastSessionClosed: true,
    onCreateNewSession: action('new session created')
  }
};

export const WithOpenSession: Story = {
  args: {
    isLoading: false,
    hasSessions: true,
    isLastSessionClosed: false,
    onCreateNewSession: action('new session created')
  }
};