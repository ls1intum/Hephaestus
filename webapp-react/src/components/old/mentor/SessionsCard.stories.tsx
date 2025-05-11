import type { Meta, StoryObj } from '@storybook/react';
import { SessionsCard } from './SessionsCard';
import type { Session } from './SessionsCard';
import { action } from '@storybook/addon-actions';

const meta: Meta<typeof SessionsCard> = {
  title: 'Components/Mentor/SessionsCard',
  component: SessionsCard,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  argTypes: {
    onSessionSelect: { action: 'session selected' },
    onCreateNewSession: { action: 'new session created' },
  }
};

export default meta;
type Story = StoryObj<typeof SessionsCard>;

// Sample data for sessions
const pastSessions: Session[] = [
  { id: 1, createdAt: '2023-05-01T09:30:00Z' },
  { id: 2, createdAt: '2023-05-03T14:15:00Z' },
  { id: 3, createdAt: '2023-05-05T11:20:00Z' },
  { id: 4, createdAt: '2023-05-08T16:45:00Z' },
  { id: 5, createdAt: '2023-05-10T10:10:00Z' },
];

export const Loading: Story = {
  args: {
    isLoading: true,
    onSessionSelect: action('session selected'),
    onCreateNewSession: action('new session created'),
  }
};

export const NoSessions: Story = {
  args: {
    sessions: [],
    isLoading: false,
    isLastSessionClosed: true,
    onSessionSelect: action('session selected'),
    onCreateNewSession: action('new session created'),
  }
};

export const WithSessions: Story = {
  args: {
    sessions: pastSessions,
    selectedSessionId: 3,
    isLoading: false,
    isLastSessionClosed: true,
    onSessionSelect: action('session selected'),
    onCreateNewSession: action('new session created'),
  }
};

export const WithOpenSession: Story = {
  args: {
    sessions: pastSessions,
    selectedSessionId: 5,
    isLoading: false,
    isLastSessionClosed: false,
    onSessionSelect: action('session selected'),
    onCreateNewSession: action('new session created'),
  }
};