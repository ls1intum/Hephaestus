import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { LeaderboardFilterSortComponent } from './sort.component';

const meta: Meta<LeaderboardFilterSortComponent> = {
  component: LeaderboardFilterSortComponent,
  tags: ['autodocs']
};

export default meta;
type Story = StoryObj<LeaderboardFilterSortComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `<app-leaderboard-filter-repository ${argsToTemplate(args)}/>`
  })
};
