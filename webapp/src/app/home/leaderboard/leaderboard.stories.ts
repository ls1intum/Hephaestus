import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { LeaderboardComponent } from './leaderboard.component';
import { LeaderboardEntry } from 'app/core/modules/openapi/model/leaderboard-entry';

const leaderboardEntries: LeaderboardEntry[] = [
  {
    githubName: 'GODrums',
    avatarUrl: 'https://avatars.githubusercontent.com/u/21990230?v=4',
    type: LeaderboardEntry.TypeEnum.User,
    name: 'Armin Stanitzok',
    score: 100,
    changesRequested: 3,
    approvals: 5,
    comments: 1,
    rank: 1
  },
  {
    githubName: 'FelixTJDietrich',
    avatarUrl: 'https://avatars.githubusercontent.com/u/5898705?v=4',
    type: LeaderboardEntry.TypeEnum.User,
    name: 'Felix T.J. Dietrich',
    score: 90,
    changesRequested: 1,
    approvals: 1,
    comments: 14,
    rank: 2
  },
  {
    githubName: 'krusche',
    avatarUrl: 'https://avatars.githubusercontent.com/u/744067?v=4',
    type: LeaderboardEntry.TypeEnum.User,
    name: 'Stephan Krusche',
    score: 50,
    changesRequested: 0,
    approvals: 3,
    comments: 1,
    rank: 3
  },
  {
    githubName: 'shadcn',
    avatarUrl: 'https://avatars.githubusercontent.com/u/124599?v=4',
    type: LeaderboardEntry.TypeEnum.User,
    name: 'shadcn',
    score: 20,
    changesRequested: 0,
    approvals: 0,
    comments: 1,
    rank: 4
  }
];

const meta: Meta<LeaderboardComponent> = {
  title: 'Components/Home/Leaderboard',
  component: LeaderboardComponent,
  tags: ['autodocs'],
  args: {
    leaderboard: leaderboardEntries
  }
};

export default meta;
type Story = StoryObj<LeaderboardComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `<app-leaderboard ${argsToTemplate(args)}/>`
  })
};
