import { Meta, StoryObj } from '@storybook/angular';
import { WorkspaceOptionComponent } from './workspace-option.component';

const meta: Meta<WorkspaceOptionComponent> = {
  component: WorkspaceOptionComponent,
  tags: ['autodocs']
};

export default meta;

type Story = StoryObj<WorkspaceOptionComponent>;

export const Default: Story = {};
