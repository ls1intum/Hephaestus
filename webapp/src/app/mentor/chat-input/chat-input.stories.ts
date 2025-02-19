import { type Meta, type StoryObj } from '@storybook/angular';
import { ChatInputComponent } from './chat-input.component';

const meta: Meta<ChatInputComponent> = {
  component: ChatInputComponent,
  tags: ['autodocs'],
  args: {
    isSending: false,
    isClosed: false
  }
};

export default meta;
type Story = StoryObj<ChatInputComponent>;

export const Default: Story = {};

export const Sending: Story = {
  args: {
    isSending: true
  }
};

export const Closed: Story = {
  args: {
    isClosed: true
  }
};
