import type { Meta, StoryObj } from '@storybook/react';
import { TeamCreateDialog } from './TeamCreateDialog';
import { action } from '@storybook/addon-actions';
import { useState } from 'react';

const meta: Meta<typeof TeamCreateDialog> = {
  title: 'Components/Teams/TeamCreateDialog',
  component: TeamCreateDialog,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  argTypes: {
    onCreate: { action: 'team created' },
  }
};

export default meta;
type Story = StoryObj<typeof TeamCreateDialog>;

// Wrapper component to handle dialog state
const DialogWrapper = (args: any) => {
  const [open, setOpen] = useState(true);
  return <TeamCreateDialog {...args} open={open} onOpenChange={setOpen} />;
};

export const Default: Story = {
  render: (args) => <DialogWrapper {...args} />,
  args: {
    onCreate: action('team created'),
    isCreating: false,
  }
};

export const Creating: Story = {
  render: (args) => <DialogWrapper {...args} />,
  args: {
    onCreate: action('team created'),
    isCreating: true,
  }
};