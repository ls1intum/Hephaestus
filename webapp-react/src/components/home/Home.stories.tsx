import type { Meta, StoryObj } from '@storybook/react';
import { Home } from './Home';

const meta = {
  title: 'Pages/Home',
  component: Home,
  parameters: {
    layout: 'fullscreen',
  },
  tags: ['autodocs'],
} satisfies Meta<typeof Home>;

export default meta;
type Story = StoryObj<typeof meta>;

export const LoggedIn: Story = {
  args: {
    username: 'John Doe',
    onViewDashboard: () => console.log('Navigating to dashboard'),
  },
};

export const Loading: Story = {
  args: {
    username: 'Loading...',
    onViewDashboard: () => console.log('View dashboard clicked'),
  },
};

export const NewUser: Story = {
  args: {
    username: 'New User',
    onViewDashboard: () => console.log('View dashboard clicked'),
  },
};

export const LongName: Story = {
  args: {
    username: 'Bartholomew Christopherson-Williamson III',
    onViewDashboard: () => console.log('View dashboard clicked'),
  },
};