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
        content: 'Hello! I’m excited to help you with your software engineering project today. What are you currently working on?',
        sessionId: 101
      },
      {
        id: 2,
        sentAt: '2024-12-05T10:16:30Z',
        sender: Message.SenderEnum.User,
        content: 'Hi! I’m struggling with designing the database schema for my project.',
        sessionId: 101
      },
      {
        id: 3,
        sentAt: '2024-12-05T10:17:00Z',
        sender: Message.SenderEnum.Mentor,
        content: 'Got it! Can you tell me a bit more about the project?',
        sessionId: 101
      },
      {
        id: 4,
        sentAt: '2024-12-05T10:17:45Z',
        sender: Message.SenderEnum.User,
        content: 'It’s an e-commerce app where users can browse products, add them to a cart, and place orders.',
        sessionId: 101
      },
      {
        id: 5,
        sentAt: '2024-12-05T10:18:10Z',
        sender: Message.SenderEnum.Mentor,
        content: 'A good first step is identifying the main entities: users, products, orders, and the cart. Let’s start with that — do you have any initial thoughts?',
        sessionId: 101
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
