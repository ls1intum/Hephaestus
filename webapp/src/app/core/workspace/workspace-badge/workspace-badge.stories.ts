import { Meta, StoryObj } from '@storybook/angular';
import { WorkspaceBadgeComponent } from './workspace-badge.component';
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

const meta: Meta<WorkspaceBadgeComponent> = {
  component: WorkspaceBadgeComponent,
  tags: ['autodocs'],
  args: {
    selectedWorkspace: workspaces[0],
    workspaces,
    select: fn(),
    signOut: fn()
  },
  argTypes: {
    selectedWorkspace: {
      control: { type: 'object' }
    },
    workspaces: {
      control: { type: 'object' }
    }
  }
};

export default meta;

type Story = StoryObj<WorkspaceBadgeComponent>;

export const Default: Story = {
  parameters: {
    viewport: {
      defaultViewport: 'responsive'
    }
  }
};

export const Mobile: Story = {
  parameters: {
    viewport: {
      defaultViewport: 'mobile1'
    }
  }
};
