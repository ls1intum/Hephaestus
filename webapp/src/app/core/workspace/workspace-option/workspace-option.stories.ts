import { Meta, StoryObj } from '@storybook/angular';
import { WorkspaceOptionComponent } from './workspace-option.component';
import { fn } from '@storybook/test';

const meta: Meta<WorkspaceOptionComponent> = {
  component: WorkspaceOptionComponent,
  tags: ['autodocs'],
  args: {
    isCompact: false,
    isSelected: false,
    iconUrl: 'https://avatars.githubusercontent.com/u/11064260?s=48&v=4',
    title: 'AET TUM',
    select: fn(),
    signOut: fn()
  }
};

export default meta;

type Story = StoryObj<WorkspaceOptionComponent>;

export const Default: Story = {};

export const IsSelected: Story = {
  args: {
    isSelected: true
  }
};

export const IsCompact: Story = {
  args: {
    isCompact: true
  },
  parameters: {
    layout: 'centered'
  }
};
