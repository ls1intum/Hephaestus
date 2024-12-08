import { type Meta, type StoryObj } from '@storybook/angular';
import { FirstSessionCardComponent } from './start-session-card.component';

const meta: Meta<FirstSessionCardComponent> = {
  component: FirstSessionCardComponent,
  tags: ['autodocs'],
  args: {}
};

export default meta;
type Story = StoryObj<FirstSessionCardComponent>;

export const Default: Story = {};
