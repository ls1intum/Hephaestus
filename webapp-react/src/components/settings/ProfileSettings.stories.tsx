import type { Meta, StoryObj } from '@storybook/react';
import { ProfileSettings } from './ProfileSettings';

const meta: Meta<typeof ProfileSettings> = {
  title: 'Settings/ProfileSettings',
  component: ProfileSettings,
  parameters: {
    layout: 'centered',
  },
};

export default meta;
type Story = StoryObj<typeof ProfileSettings>;

export const Default: Story = {
  args: {
    user: {
      name: 'Jane Smith',
      email: 'jane.smith@example.com',
      avatar: 'https://github.com/identicons/app/oauth_app/1'
    },
  },
};

export const NoAvatar: Story = {
  args: {
    user: {
      name: 'John Doe',
      email: 'john.doe@example.com',
    },
  },
};

export const NewUser: Story = {
  args: {},
};