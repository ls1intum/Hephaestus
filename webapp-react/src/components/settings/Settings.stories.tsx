import type { Meta, StoryObj } from '@storybook/react';
import { Settings } from './Settings';

const meta: Meta<typeof Settings> = {
  title: 'Pages/Settings',
  component: Settings,
  parameters: {
    layout: 'fullscreen',
  },
};

export default meta;
type Story = StoryObj<typeof Settings>;

export const Default: Story = {
  args: {
    user: {
      name: 'Jane Smith',
      email: 'jane.smith@example.com',
      avatar: 'https://github.com/identicons/app/oauth_app/1'
    }
  },
};

export const NoUser: Story = {
  args: {}
};