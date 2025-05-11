import type { Meta, StoryObj } from '@storybook/react';
import { NotificationSettings } from './NotificationSettings';

const meta: Meta<typeof NotificationSettings> = {
  title: 'Settings/NotificationSettings',
  component: NotificationSettings,
  parameters: {
    layout: 'centered',
  },
};

export default meta;
type Story = StoryObj<typeof NotificationSettings>;

export const Default: Story = {
  args: {},
};