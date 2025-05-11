import type { Meta, StoryObj } from '@storybook/react';
import { SecuritySettings } from './SecuritySettings';

const meta: Meta<typeof SecuritySettings> = {
  title: 'Settings/SecuritySettings',
  component: SecuritySettings,
  parameters: {
    layout: 'centered',
  },
};

export default meta;
type Story = StoryObj<typeof SecuritySettings>;

export const Default: Story = {
  args: {},
};