import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { LeaderboardOverviewComponent } from './leaderboard-overview.component';

type FlatArgs = {
  rank: number;
  score: number;
  userLogin: string;
  userName: string;
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
      }
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
