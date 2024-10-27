import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { LeaderboardComponent } from './leaderboard.component';
import { LeaderboardEntry } from 'app/core/modules/openapi/model/leaderboard-entry';

const leaderboardEntries: LeaderboardEntry[] = [
  {
    rank: 1,
    score: 100,
    user: {
      id: 0,
      login: 'GODrums',
      avatarUrl: 'https://avatars.githubusercontent.com/u/21990230?v=4',
      name: 'Armin Stanitzok',
      htmlUrl: 'https://github.com/GODrums'
    },
    numberOfReviewedPRs: 18,
    numberOfApprovals: 8,
    numberOfChangeRequests: 7,
    numberOfComments: 2,
    numberOfUnknowns: 1,
    numberOfCodeComments: 5
  },
  {
    rank: 2,
    score: 90,
    user: {
      id: 1,
      login: 'FelixTJDietrich',
      avatarUrl: 'https://avatars.githubusercontent.com/u/5898705?v=4',
      name: 'Felix T.J. Dietrich',
      htmlUrl: 'https://github.com/FelixTJDietrich'
    },
    numberOfReviewedPRs: 8,
    numberOfApprovals: 1,
    numberOfChangeRequests: 5,
    numberOfComments: 2,
    numberOfUnknowns: 0,
    numberOfCodeComments: 21
  },
  {
    rank: 3,
    score: 50,
    user: {
      id: 2,
      login: 'krusche',
      avatarUrl: 'https://avatars.githubusercontent.com/u/744067?v=4',
      name: 'Stephan Krusche',
      htmlUrl: 'https://github.com/krusche'
    },
    numberOfReviewedPRs: 5,
    numberOfApprovals: 3,
    numberOfChangeRequests: 1,
    numberOfComments: 0,
    numberOfUnknowns: 0,
    numberOfCodeComments: 2
  },
  {
    rank: 4,
    score: 20,
    user: {
      id: 3,
      login: 'shadcn',
      avatarUrl: 'https://avatars.githubusercontent.com/u/124599?v=4',
      name: 'shadcn',
      htmlUrl: 'https://github.com/shadcn'
    },
    numberOfReviewedPRs: 3,
    numberOfApprovals: 0,
    numberOfChangeRequests: 1,
    numberOfComments: 1,
    numberOfUnknowns: 1,
    numberOfCodeComments: 5
  },
  {
    rank: 5,
    score: 0,
    user: {
      id: 3,
      login: 'doesnotexistongithub',
      avatarUrl: 'https://avatars.githubusercontentd.com/u/13132323124599?v=4',
      name: 'NoAvatarUser',
      htmlUrl: 'https://github.com/NoAvatarUser'
    },
    numberOfReviewedPRs: 0,
    numberOfApprovals: 0,
    numberOfChangeRequests: 0,
    numberOfComments: 0,
    numberOfUnknowns: 0,
    numberOfCodeComments: 0
  }
];

const meta: Meta<LeaderboardComponent> = {
  component: LeaderboardComponent,
  tags: ['autodocs'],
  args: {
    leaderboard: leaderboardEntries,
    isLoading: false
  },
  argTypes: {
    leaderboard: {
      control: 'object'
    },
    isLoading: {
      control: 'boolean'
    }
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

export const Loading: Story = {
  args: {
    isLoading: true
  },
  render: (args) => ({
    props: args,
    template: `<app-leaderboard ${argsToTemplate(args)}/>`
  })
};
