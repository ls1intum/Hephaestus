import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { LeaderboardOverviewComponent } from './leaderboard-overview.component';
import { PullRequestInfo } from '../reviews-popover/reviews-popover.component';

type FlatArgs = {
  rank: number;
  score: number;
  userLogin: string;
  userName: string;
  reviewedPullRequests: Array<PullRequestInfo>;
  numberOfReviewedPRs: number;
  numberOfApprovals: number;
  numberOfChangeRequests: number;
  numberOfComments: number;
  numberOfUnknowns: number;
  numberOfCodeComments: number;
  leaguePoints: number;
};

function flatArgsToProps(args: FlatArgs) {
  return {
    leaderboardEntry: {
      rank: args.rank,
      score: args.score,
      user: {
        id: 0,
        login: args.userLogin,
        avatarUrl: `https://github.com/${args.userLogin.toLowerCase()}.png`,
        name: args.userName,
        htmlUrl: `https://github.com/${args.userLogin}`
      },
      reviewedPullRequests: args.reviewedPullRequests,
      numberOfReviewedPRs: args.numberOfReviewedPRs,
      numberOfApprovals: args.numberOfApprovals,
      numberOfChangeRequests: args.numberOfChangeRequests,
      numberOfComments: args.numberOfComments,
      numberOfUnknowns: args.numberOfUnknowns,
      numberOfCodeComments: args.numberOfUnknowns
    },
    leaguePoints: args.leaguePoints
  };
}

const meta: Meta<FlatArgs> = {
  component: LeaderboardOverviewComponent,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered'
  },
  args: {
    rank: 1,
    score: 100,
    userLogin: 'GODrums',
    userName: 'Armin Stanitzok',
    reviewedPullRequests: [
      {
        id: 1,
        repository: {
          name: 'Artemis'
        },
        number: 9231,
        title: 'Fix Artemis',
        htmlUrl: 'https://www.github.com/ls1intum/Artemis/pull/9231'
      },
      {
        id: 2,
        repository: {
          name: 'Hephaestus'
        },
        number: 132,
        title: 'Fix Hephaestus',
        htmlUrl: 'https://www.github.com/ls1intum/Hephaestus/pull/132'
      },
      {
        id: 3,
        repository: {
          name: 'Artemis'
        },
        number: 9232,
        title: 'Fix Artemis',
        htmlUrl: 'https://www.github.com/ls1intum/Artemis/pull/9232'
      }
    ],
    numberOfReviewedPRs: 18,
    numberOfApprovals: 8,
    numberOfChangeRequests: 7,
    numberOfComments: 2,
    numberOfUnknowns: 1,
    numberOfCodeComments: 5,
    leaguePoints: 1100
  },
  argTypes: {
    rank: {
      control: {
        type: 'number'
      }
    },
    score: {
      control: {
        type: 'number'
      }
    },
    userLogin: {
      control: {
        type: 'text'
      }
    },
    userName: {
      control: {
        type: 'text'
      }
    },
    reviewedPullRequests: {
      control: {
        type: 'object'
      }
    },
    numberOfReviewedPRs: {
      control: {
        type: 'number'
      }
    },
    numberOfApprovals: {
      control: {
        type: 'number'
      }
    },
    numberOfChangeRequests: {
      control: {
        type: 'number'
      }
    },
    numberOfComments: {
      control: {
        type: 'number'
      }
    },
    numberOfUnknowns: {
      control: {
        type: 'number'
      }
    },
    numberOfCodeComments: {
      control: {
        type: 'number'
      }
    },
    leaguePoints: {
      control: {
        type: 'number'
      }
    }
  }
};

export default meta;
type Story = StoryObj<LeaderboardOverviewComponent>;

export const Default: Story = {
  render: (args) => ({
    props: flatArgsToProps(args as unknown as FlatArgs),
    template: `<app-leaderboard-overview ${argsToTemplate(flatArgsToProps(args as unknown as FlatArgs))} />`
  })
};
