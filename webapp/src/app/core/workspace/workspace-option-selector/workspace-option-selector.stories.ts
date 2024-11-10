import { Meta, StoryObj } from '@storybook/angular';
import { WorkspaceOptionSelectorComponent } from './workspace-option-selector.component';
import { fn } from '@storybook/test';

const workspaces = [
  {
    id: '1',
    title: 'AET TUM',
    iconUrl: 'https://avatars.githubusercontent.com/u/11064260?s=48&v=4'
  },
  {
    id: '2',
    title: 'Hephaestus',
    iconUrl: ''
  },
  {
    id: '3',
    title: 'Intro Course',
    iconUrl: 'https://avatars.githubusercontent.com/u/11064260?s=48&v=4'
  }
];

const meta: Meta<WorkspaceOptionSelectorComponent> = {
  component: WorkspaceOptionSelectorComponent,
  tags: ['autodocs'],
  args: {
    isCompact: false,
    selectedWorkspace: workspaces[0],
    workspaces,
    onSelect: fn(),
    onSignOut: fn()
  },
  argTypes: {
    isCompact: {
      control: { type: 'boolean' }
    },
    selectedWorkspace: {
      control: { type: 'object' }
    },
    workspaces: {
      control: { type: 'object' }
    }
  }
};

export default meta;

type Story = StoryObj<WorkspaceOptionSelectorComponent>;

export const Default: Story = {};

export const IsCompact: Story = {
  args: {
    isCompact: true
  },
  parameters: {
    layout: 'centered'
  }
};
