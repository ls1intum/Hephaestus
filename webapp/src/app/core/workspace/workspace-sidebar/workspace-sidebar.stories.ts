import { Meta, StoryObj } from '@storybook/angular';
import { WorkspaceSidebarComponent } from './workspace-sidebar.component';

const meta: Meta<WorkspaceSidebarComponent> = {
  component: WorkspaceSidebarComponent,
  tags: ['autodocs']
};

export default meta;

type Story = StoryObj<WorkspaceSidebarComponent>;

export const Default: Story = {};
