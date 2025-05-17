import type { Meta, StoryObj } from '@storybook/react';
import { fn } from '@storybook/test';
import { LandingPage } from './LandingPage';

/**
 * Landing page component that serves as the main entry point for new users.
 * Showcases product features and provides sign-in functionality.
 */
const meta = {
  component: LandingPage,
  tags: ['autodocs'],
  argTypes: {
    onSignIn: {
      description: 'Callback function triggered when the sign-in button is clicked',
      action: 'signed in',
    },
    isSignedIn: {
      description: 'Whether the user is currently signed in',
      control: 'boolean',
    }
  },
  args: {
    onSignIn: fn(),
  }
} satisfies Meta<typeof LandingPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default landing page view for anonymous users.
 */
/**
 * Default landing page view for non-authenticated visitors.
 */
export const Default: Story = {
  args: {
    isSignedIn: false,
  },
};

/**
 * Landing page view for users who are already signed in.
 */
export const SignedIn: Story = {
  args: {
    isSignedIn: true,
  },
};