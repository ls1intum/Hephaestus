import { Meta, StoryObj } from '@storybook/angular';
import { WorkspaceBadgeComponent } from './workspace-badge.component';

const meta: Meta<WorkspaceBadgeComponent> = {
  component: WorkspaceBadgeComponent,
  tags: ['autodocs'],
  args: {
    iconUrl: 'https://avatars.githubusercontent.com/u/11064260?s=48&v=4',
    title: 'AET TUM'
  },
  argTypes: {
    iconUrl: {
      control: { type: 'text' }
    },
    title: {
      control: { type: 'text' }
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
