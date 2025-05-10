import type { Meta, StoryObj } from '@storybook/react';
import { action } from '@storybook/addon-actions';
import { LandingPage } from './LandingPage';

const meta: Meta<typeof LandingPage> = {
  title: 'Landing/LandingPage',
  component: LandingPage,
  parameters: {
    layout: 'fullscreen',
  },
};

export default meta;
type Story = StoryObj<typeof LandingPage>;

export const Default: Story = {
  args: {
    onSignIn: () => action('signIn')(),
  },
};