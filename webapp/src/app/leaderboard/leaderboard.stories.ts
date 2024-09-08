import { type Meta, type StoryObj } from '@storybook/angular';
import { LeaderboardComponent } from './leaderboard.component';

const meta: Meta<LeaderboardComponent> = {
  title: 'Pages/Leaderboard',
  component: LeaderboardComponent,
  tags: ['autodocs']
};

export default meta;
type Story = StoryObj<LeaderboardComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `<app-leaderboard />`
  })
};
