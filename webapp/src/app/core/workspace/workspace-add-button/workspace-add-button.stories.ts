import { Meta, StoryObj } from '@storybook/angular';
import { WorkspaceAddButtonComponent } from './workspace-add-button.component';

const meta: Meta<WorkspaceAddButtonComponent> = {
  component: WorkspaceAddButtonComponent,
  tags: ['autodocs'],
  args: {
    isCompact: false
  },
  argTypes: {
    isCompact: {
      control: { type: 'boolean' }
    }
  }
};

export default meta;

type Story = StoryObj<WorkspaceAddButtonComponent>;

export const Default: Story = {};

export const IsCompact: Story = {
  args: {
    isCompact: true
  }
};
