import { Meta, StoryObj } from '@storybook/angular';
import { WorkspaceBadgeComponent } from './workspace-badge.component';

const meta: Meta<WorkspaceBadgeComponent> = {
  component: WorkspaceBadgeComponent,
  tags: ['autodocs']
};

export default meta;

type Story = StoryObj<WorkspaceBadgeComponent>;

export const Default: Story = {};
