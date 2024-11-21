import { type Meta, type StoryObj } from '@storybook/angular';
import { LeaderboardLegendComponent } from './legends.component';

const meta: Meta<LeaderboardLegendComponent> = {
  component: LeaderboardLegendComponent,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered'
  }
};

export default meta;
type Story = StoryObj<LeaderboardLegendComponent>;

export const Default: Story = {};
