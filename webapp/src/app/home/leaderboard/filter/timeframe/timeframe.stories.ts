import { type Meta, type StoryObj } from '@storybook/angular';
import { LeaderboardFilterTimeframeComponent } from './timeframe.component';

const meta: Meta<LeaderboardFilterTimeframeComponent> = {
  component: LeaderboardFilterTimeframeComponent,
  tags: ['autodocs'],
  argTypes: {
    after: {
      control: {
        type: 'text'
      },
      description: 'Left limit of the timeframe'
    },
    before: {
      control: {
        type: 'text'
      },
      description: 'Right limit of the timeframe'
    }
  }
};

export default meta;
type Story = StoryObj<LeaderboardFilterTimeframeComponent>;

export const Default: Story = {
  render: () => ({
    template: '<app-leaderboard-filter-timeframe />'
  })
};
