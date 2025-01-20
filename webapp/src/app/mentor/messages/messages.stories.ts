import { type Meta, type StoryObj } from '@storybook/angular';
import { MessagesComponent } from './messages.component';
import { Message } from '@app/core/modules/openapi';

const meta: Meta<MessagesComponent> = {
  component: MessagesComponent,
  tags: ['autodocs'],
  args: {
    messages: [
      {
        id: 1,
        sentAt: '2024-12-05T10:15:00Z',
        sender: Message.SenderEnum.Mentor,
        content: 'Good morning! How are you today?',
        sessionId: 101
      },
      {
        id: 2,
        sentAt: '2024-12-05T10:15:30Z',
        sender: Message.SenderEnum.User,
        content: 'Good morning! I’m doing well, thank you!',
        sessionId: 101
      },
      {
        id: 3,
        sentAt: '2024-12-05T10:16:00Z',
        sender: Message.SenderEnum.Mentor,
        content: 'Great to hear! Can you give me a quick status update on your project?',
        sessionId: 101
      },
      {
        id: 4,
        sentAt: '2024-12-05T10:16:30Z',
        sender: Message.SenderEnum.User,
        content: 'Sure! I’ve completed the database schema and started working on the API design.',
        sessionId: 101
      },
      {
        id: 5,
        sentAt: '2024-12-05T10:16:30Z',
        sender: Message.SenderEnum.Mentor,
        content: 'Keep up the good work!',
        sessionId: 101,
        status: ['Database schema completed', 'API design started'],
        impediments: [],
        promises: []
      }
    ]
  }
};

export default meta;
type Story = StoryObj<MessagesComponent>;

export const Default: Story = {};

export const isLoading: Story = {
  args: {
    isLoading: true
  }
};
