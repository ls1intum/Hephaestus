import type { Meta, StoryObj } from '@storybook/react';
import { ChatInput } from './ChatInput';
import { action } from '@storybook/addon-actions';

const meta: Meta<typeof ChatInput> = {
  title: 'Components/Chat/ChatInput',
  component: ChatInput,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  argTypes: {
    onSendMessage: { action: 'message sent' },
  }
};

export default meta;
type Story = StoryObj<typeof ChatInput>;

export const Default: Story = {
  args: {
    isClosed: false,
    isSending: false,
    onSendMessage: action('message sent')
  }
};

export const Sending: Story = {
  args: {
    isClosed: false,
    isSending: true,
    onSendMessage: action('message sent')
  }
};

export const Closed: Story = {
  args: {
    isClosed: true,
    isSending: false,
    onSendMessage: action('message sent')
  }
};

export const DisabledWhileSendingAndClosed: Story = {
  args: {
    isClosed: true,
    isSending: true,
    onSendMessage: action('message sent')
  }
};