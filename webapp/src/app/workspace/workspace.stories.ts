import { type Meta, type StoryObj } from '@storybook/angular';
import { WorkspaceComponent } from './workspace.component';

const meta: Meta<WorkspaceComponent> = {
  component: WorkspaceComponent,
  tags: ['autodocs']
};

export default meta;
type Story = StoryObj<WorkspaceComponent>;

export const Default: Story = {};
