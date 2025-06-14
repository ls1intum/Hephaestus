import { Meta, StoryObj } from '@storybook/angular';
import { WorkspaceThumbComponent } from './workspace-thumb.component';
import { fn } from '@storybook/test';

const meta: Meta<WorkspaceThumbComponent> = {
  component: WorkspaceThumbComponent,
  tags: ['autodocs'],
  args: {
    isSelected: false,
    hoverRingEnabled: true,
    iconUrl: 'https://avatars.githubusercontent.com/u/11064260?s=48&v=4',
    select: fn()
  },
  argTypes: {
    isSelected: {
      control: { type: 'boolean' }
    },
    hoverRingEnabled: {
      control: { type: 'boolean' }
    },
    iconUrl: {
      control: { type: 'text' }
    }
  }
};

export default meta;

type Story = StoryObj<WorkspaceThumbComponent>;

export const Default: Story = {};

export const Selected: Story = {
  args: {
    isSelected: true
  }
};

export const RingDisabled: Story = {
  args: {
    hoverRingEnabled: false
  }
};

export const NoIcon: Story = {
  args: {
    iconUrl: ''
  }
};
