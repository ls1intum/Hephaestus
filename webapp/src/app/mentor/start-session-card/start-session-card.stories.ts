import { type Meta, type StoryObj } from '@storybook/angular';
import { StartSessionCardComponent } from './start-session-card.component';

const meta: Meta<StartSessionCardComponent> = {
  component: StartSessionCardComponent,
  tags: ['autodocs'],
  args: {}
};

export default meta;
type Story = StoryObj<StartSessionCardComponent>;

export const Default: Story = {
  args: {
    hasSessions: true,
    isLastSessionClosed: true
  }
};

export const StartFirstSession: Story = {
  args: {
    hasSessions: false,
    isLastSessionClosed: true
  }
};

export const IsLoading: Story = {
  args: {
    isLoading: true,
    isLastSessionClosed: true
  }
};

export const LastSessionOpen: Story = {
  args: {
    hasSessions: true,
    isLastSessionClosed: false
  }
};
