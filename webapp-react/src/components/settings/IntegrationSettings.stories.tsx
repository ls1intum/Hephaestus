import type { Meta, StoryObj } from '@storybook/react';
import { IntegrationSettings } from './IntegrationSettings';

const meta: Meta<typeof IntegrationSettings> = {
  title: 'Settings/IntegrationSettings',
  component: IntegrationSettings,
  parameters: {
    layout: 'centered',
  },
};

export default meta;
type Story = StoryObj<typeof IntegrationSettings>;

export const Default: Story = {
  args: {},
};