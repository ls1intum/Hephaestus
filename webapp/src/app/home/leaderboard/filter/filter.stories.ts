import { type Meta, type StoryObj } from '@storybook/angular';
import { LeaderboardFilterComponent } from './filter.component';

const meta: Meta<LeaderboardFilterComponent> = {
  component: LeaderboardFilterComponent,
  tags: ['autodocs']
};

export default meta;
type Story = StoryObj<LeaderboardFilterComponent>;

export const Default: Story = {
  render: () => ({
    template: '<app-leaderboard-filter />'
  })
};
