import type { Meta, StoryObj } from '@storybook/react';
import { Messages } from './Messages';
import { MessageSender } from './types';

const meta: Meta<typeof Messages> = {
  title: 'Components/Chat/Messages',
  component: Messages,
  tags: ['autodocs'],
  parameters: {
    layout: 'padded',
  },
};

export default meta;
type Story = StoryObj<typeof Messages>;

export const Loading: Story = {
  args: {
    isLoading: true,
    messages: []
  }
};

export const EmptyChat: Story = {
  args: {
    messages: []
  }
};

export const BasicConversation: Story = {
  args: {
    messages: [
      {
        id: '1',
        content: 'Hello! I could use some help with implementing a feature in React.',
        sender: MessageSender.User,
        sentAt: new Date(2023, 4, 15, 10, 30).toISOString()
      },
      {
        id: '2',
        content: 'Hi there! I\'d be happy to help you implement a feature in React. Could you please describe what you\'re trying to build?',
        sender: MessageSender.Mentor,
        sentAt: new Date(2023, 4, 15, 10, 31).toISOString()
      },
      {
        id: '3',
        content: 'I need to build a form component with validation that submits data to an API.',
        sender: MessageSender.User,
        sentAt: new Date(2023, 4, 15, 10, 33).toISOString()
      },
      {
        id: '4',
        content: 'For building a form component with validation in React, you have several options. Let me walk you through one approach using React Hook Form and Zod for validation.',
        sender: MessageSender.Mentor,
        sentAt: new Date(2023, 4, 15, 10, 34).toISOString()
      }
    ]
  }
};

export const WithSummary: Story = {
  args: {
    messages: [
      {
        id: '1',
        content: 'What have I been working on recently?',
        sender: MessageSender.User,
        sentAt: new Date(2023, 4, 15, 10, 30).toISOString()
      },
      {
        id: '2',
        content: JSON.stringify({
          response: 'Here\'s a summary of what you\'ve been working on:',
          status: [
            {
              title: 'UI Component Library',
              description: 'You\'ve been working on migrating the component library from Angular to React with Storybook integration.'
            },
            {
              title: 'API Integration',
              description: 'You\'ve implemented several API endpoints for user authentication.'
            }
          ],
          impediments: [
            {
              title: 'Test Coverage',
              description: 'The current test coverage is below the target of 80%.'
            }
          ],
          promises: [
            {
              title: 'Documentation',
              description: 'I\'ll help you generate comprehensive documentation for your components.'
            },
            {
              title: 'Test Strategy',
              description: 'I can suggest a testing strategy to improve test coverage.'
            }
          ]
        }),
        sender: MessageSender.Mentor,
        sentAt: new Date(2023, 4, 15, 10, 31).toISOString()
      }
    ]
  }
};

export const WithPullRequests: Story = {
  args: {
    messages: [
      {
        id: '1',
        content: 'Show me my active pull requests.',
        sender: MessageSender.User,
        sentAt: new Date(2023, 4, 15, 10, 30).toISOString()
      },
      {
        id: '2',
        content: JSON.stringify({
          response: 'Here are your active pull requests:',
          development: [
            {
              url: 'https://github.com/org/repo/pull/123',
              repo: 'org/repo',
              number: 123,
              title: 'Feature: Add new authentication flow',
              status: 'OPEN',
              created_at: '2023-05-10T14:30:00Z'
            },
            {
              url: 'https://github.com/org/repo/pull/124',
              repo: 'org/repo',
              number: 124,
              title: 'Fix: Resolve memory leak in useEffect',
              status: 'OPEN',
              created_at: '2023-05-12T09:15:00Z'
            },
            {
              url: 'https://github.com/org/repo/pull/125',
              repo: 'org/design-system',
              number: 125,
              title: 'Docs: Update component documentation',
              status: 'CLOSED',
              created_at: '2023-05-08T11:20:00Z'
            }
          ]
        }),
        sender: MessageSender.Mentor,
        sentAt: new Date(2023, 4, 15, 10, 31).toISOString()
      }
    ]
  }
};

export const MixedContentTypesConversation: Story = {
  args: {
    messages: [
      {
        id: '1',
        content: 'Hello! Can you summarize my recent work?',
        sender: MessageSender.User,
        sentAt: new Date(2023, 4, 15, 10, 30).toISOString()
      },
      {
        id: '2',
        content: JSON.stringify({
          response: 'Here\'s a summary of your recent work:',
          status: [
            {
              title: 'Component Migration',
              description: 'You\'ve been migrating Angular components to React.'
            }
          ],
          impediments: [],
          promises: [
            {
              title: 'Next Steps',
              description: 'I can help identify remaining components to migrate.'
            }
          ]
        }),
        sender: MessageSender.Mentor,
        sentAt: new Date(2023, 4, 15, 10, 31).toISOString()
      },
      {
        id: '3',
        content: 'Great! And what are my open pull requests?',
        sender: MessageSender.User,
        sentAt: new Date(2023, 4, 15, 10, 33).toISOString()
      },
      {
        id: '4',
        content: JSON.stringify({
          response: 'Here are your open pull requests:',
          development: [
            {
              url: 'https://github.com/org/repo/pull/126',
              repo: 'org/repo',
              number: 126,
              title: 'Migrate LeagueIcon component to React',
              status: 'OPEN',
              created_at: '2023-05-14T15:45:00Z'
            }
          ]
        }),
        sender: MessageSender.Mentor,
        sentAt: new Date(2023, 4, 15, 10, 34).toISOString()
      },
      {
        id: '5',
        content: 'Thanks! I\'ll review those pull requests now.',
        sender: MessageSender.User,
        sentAt: new Date(2023, 4, 15, 10, 35).toISOString()
      },
      {
        id: '6',
        content: 'Great! Let me know if you need any help with the review process or have any questions about the implementation details.',
        sender: MessageSender.Mentor,
        sentAt: new Date(2023, 4, 15, 10, 36).toISOString()
      }
    ]
  }
};