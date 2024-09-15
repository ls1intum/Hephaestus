import { type Meta, type StoryObj } from '@storybook/angular';
import { LeaderboardComponent } from './leaderboard.component';

const meta: Meta<LeaderboardComponent> = {
  title: 'Components/Home/Leaderboard',
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
