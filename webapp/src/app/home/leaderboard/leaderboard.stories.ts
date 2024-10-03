import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { LeaderboardComponent } from './leaderboard.component';
import { LeaderboardEntry } from 'app/core/modules/openapi/model/leaderboard-entry';
import { PullRequestReviewDTO } from 'app/core/modules/openapi/model/pull-request-review-dto';

let reviewIdCounter = 1;

const generateReviews = (count: number, state: PullRequestReviewDTO.StateEnum): PullRequestReviewDTO[] => {
  return Array.from({ length: count }, () => ({
    id: reviewIdCounter++,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    submittedAt: new Date().toISOString(),
    state
  }));
};

const leaderboardEntries: LeaderboardEntry[] = [
  {
    githubName: 'GODrums',
    avatarUrl: 'https://avatars.githubusercontent.com/u/21990230?v=4',
    type: LeaderboardEntry.TypeEnum.User,
    name: 'Armin Stanitzok',
    score: 100,
    changesRequested: generateReviews(3, PullRequestReviewDTO.StateEnum.ChangesRequested),
    approvals: generateReviews(5, PullRequestReviewDTO.StateEnum.Approved),
    comments: generateReviews(1, PullRequestReviewDTO.StateEnum.Commented),
    rank: 1,
    numberOfReviewedPRs: 10
  },
  {
    githubName: 'FelixTJDietrich',
    avatarUrl: 'https://avatars.githubusercontent.com/u/5898705?v=4',
    type: LeaderboardEntry.TypeEnum.User,
    name: 'Felix T.J. Dietrich',
    score: 90,
    changesRequested: generateReviews(1, PullRequestReviewDTO.StateEnum.ChangesRequested),
    approvals: generateReviews(1, PullRequestReviewDTO.StateEnum.Approved),
    comments: generateReviews(14, PullRequestReviewDTO.StateEnum.Commented),
    rank: 2,
    numberOfReviewedPRs: 16
  },
  {
    githubName: 'krusche',
    avatarUrl: 'https://avatars.githubusercontent.com/u/744067?v=4',
    type: LeaderboardEntry.TypeEnum.User,
    name: 'Stephan Krusche',
    score: 50,
    changesRequested: [],
    approvals: generateReviews(3, PullRequestReviewDTO.StateEnum.Approved),
    comments: generateReviews(1, PullRequestReviewDTO.StateEnum.Commented),
    rank: 3,
    numberOfReviewedPRs: 3
  },
  {
    githubName: 'shadcn',
    avatarUrl: 'https://avatars.githubusercontent.com/u/124599?v=4',
    type: LeaderboardEntry.TypeEnum.User,
    name: 'shadcn',
    score: 20,
    changesRequested: [],
    approvals: [],
    comments: generateReviews(1, PullRequestReviewDTO.StateEnum.Commented),
    rank: 4,
    numberOfReviewedPRs: 1
  },
  {
    githubName: 'doesnotexistongithub',
    avatarUrl: 'https://avatars.githubusercontentd.com/u/13132323124599?v=4',
    type: LeaderboardEntry.TypeEnum.User,
    name: 'NoAvatarUser',
    score: 10,
    changesRequested: [],
    approvals: [],
    comments: generateReviews(1, PullRequestReviewDTO.StateEnum.Commented),
    rank: 5,
    numberOfReviewedPRs: 0
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
