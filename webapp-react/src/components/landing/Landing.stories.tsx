import type { Meta, StoryObj } from '@storybook/react';
import { Landing } from './Landing';

const meta: Meta<typeof Landing> = {
  title: 'Pages/Landing',
  component: Landing,
  parameters: {
    layout: 'fullscreen',
  },
};

export default meta;
type Story = StoryObj<typeof Landing>;

export const Default: Story = {
  args: {
    onLogin: () => console.log('Login clicked'),
    onSignup: () => console.log('Signup clicked'),
  },
};

export const NoCallbacks: Story = {
  args: {},
  name: 'Without Callbacks',
};