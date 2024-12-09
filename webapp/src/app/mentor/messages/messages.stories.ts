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
        content: 'Hello! How can I assist you today?',
        sessionId: 101
      },
      {
        id: 2,
        sentAt: '2024-12-05T10:16:30Z',
        sender: Message.SenderEnum.User,
        content: 'I need help with understanding my recent order.',
        sessionId: 101
      },
      {
        id: 3,
        sentAt: '2024-12-05T10:17:00Z',
        sender: Message.SenderEnum.Mentor,
        content: 'Sure! Could you provide your order ID?',
        sessionId: 101
      },
      {
        id: 4,
        sentAt: '2024-12-05T10:17:45Z',
        sender: Message.SenderEnum.User,
        content: 'The order ID is #12345. I’m looking for the details.',
        sessionId: 101
      },
      {
        id: 5,
        sentAt: '2024-12-05T10:18:10Z',
        sender: Message.SenderEnum.Mentor,
        content: "Got it! Please hold on while I fetch your details. Thank you for your patience. :) I'll be back in a moment...",
        sessionId: 101
      }
    ]
  }
};

export default meta;
type Story = StoryObj<MessagesComponent>;

export const Default: Story = {};
