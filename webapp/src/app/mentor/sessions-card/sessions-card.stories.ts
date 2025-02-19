import { type Meta, type StoryObj } from '@storybook/angular';
import { SessionsCardComponent } from './sessions-card.component';

const meta: Meta<SessionsCardComponent> = {
  component: SessionsCardComponent,
  tags: ['autodocs'],
  args: {
    selectedSessionId: 2,
    sessions: [
      {
        id: 1,
        createdAt: '2024-12-05T10:15:00Z',
        isClosed: false
      },
      {
        id: 2,
        createdAt: '2024-12-05T10:16:30Z',
        isClosed: false
      },
      {
        id: 3,
        createdAt: '2024-12-05T10:17:00Z',
        isClosed: false
      },
      {
        id: 4,
        createdAt: '2024-12-05T10:17:45Z',
        isClosed: false
      },
      {
        id: 5,
        createdAt: '2024-12-05T10:18:10Z',
        isClosed: false
      }
    ]
  }
};

export default meta;
type Story = StoryObj<SessionsCardComponent>;

export const Default: Story = {};

export const IsLoading: Story = {
  args: {
    isLoading: true,
    sessions: [],
    selectedSessionId: undefined
  }
};

export const Empty: Story = {
  args: {
    sessions: [],
    selectedSessionId: undefined
  }
};
