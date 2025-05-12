import type { Meta, StoryObj } from '@storybook/react';
import { ScoringExplanationDialog } from './ScoringExplanationDialog';
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { GraphIcon } from '@primer/octicons-react';

const meta: Meta<typeof ScoringExplanationDialog> = {
  title: 'Leaderboard/ScoringExplanationDialog',
  component: ScoringExplanationDialog,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  decorators: [
    (Story) => (
      <div className="p-6 max-w-sm">
        <Story />
      </div>
    ),
  ],
};

export default meta;
type Story = StoryObj<typeof ScoringExplanationDialog>;

// Simple story variant that shows the dialog in open state
export const Default: Story = {
  args: {
    open: true,
    onOpenChange: () => {},
  },
};

// Interactive story variant with a button to open/close the dialog
export const Interactive: Story = {
  render: () => {
    // eslint-disable-next-line react-hooks/rules-of-hooks
    const [open, setOpen] = useState(false);

    return (
      <div className="flex flex-col items-center gap-4">
        <Button 
          variant="outline" 
          onClick={() => setOpen(true)}
          className="flex items-center gap-2"
        >
          <GraphIcon className="h-4 w-4" />
          View Scoring Formula
        </Button>
        <ScoringExplanationDialog
          open={open}
          onOpenChange={setOpen}
        />
      </div>
    );
  },
};

// Story variant that shows the dialog in closed state
export const Closed: Story = {
  args: {
    open: false,
    onOpenChange: () => {},
  },
};