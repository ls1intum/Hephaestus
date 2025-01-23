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

export const WithSummary: Story = {
  args: {
    messages: [
      {
        id: 1,
        sentAt: '2024-12-05T10:17:00Z',
        sender: Message.SenderEnum.Mentor,
        content:
          "SUMMARY\nSTATUS\n✅ Focused on refining chatbot's NLP responses and adding user authentication features.\nIMPEDIMENTS\n⚠️ Handling ambiguous user inputs causing challenges for Dialogflow in determining correct intents due to variations in phrasing and context.\nPROMISES\n🏁 Continuation of testing and refining chatbot's response flow to address ambiguity in user inputs.\nTEXT\nGreat progress on multiple fronts with the chatbot development! If there's anything else you'd like to add or discuss further, feel free to share.",
        sessionId: 101
      }
    ]
  }
};

export const WithPrOverview: Story = {
  args: {
    messages: [
      {
        id: 1,
        sentAt: '2024-12-05T10:17:00Z',
        sender: Message.SenderEnum.Mentor,
        content:
          'DEVELOPMENT\n\nPR\nNumber: 230\nTitle: Testing of the GitHub Integration Functionality for AI Mentor\nState: OPEN\nDraft: true\nMerged: false\nURL: https://github.com/ls1intum/Hephaestus/pull/230\n\n---\n\nPR\nNumber: 220\nTitle: AI Mentor memory integration\nState: OPEN\nDraft: false\nMerged: false\nURL: https://github.com/ls1intum/Hephaestus/pull/220\n\nRESPONSE\n\nI have found you worked on these PRs during the last spring. Do you want me to add them to your status update?',
        sessionId: 101
      }
    ]
  }
};
