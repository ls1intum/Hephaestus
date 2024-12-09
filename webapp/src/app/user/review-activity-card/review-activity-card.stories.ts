import { argsToTemplate, Meta, StoryObj } from '@storybook/angular';
import { ReviewActivityCardComponent } from './review-activity-card.component';
import dayjs from 'dayjs';

type FlatArgs = {
  isLoading: boolean;
  reviewActivityCreatedAt: string;
  reviewActivityState: string;
  reviewActivityScore: number;
  pullRequestNumber: number;
  pullRequestState: string;
  pullRequestUrl: string;
  pullRequestTitle: string;
  repositoryName: string;
};

function flatArgsToProps(args: FlatArgs) {
  return {
    isLoading: args.isLoading,
    createdAt: dayjs(args.reviewActivityCreatedAt),
    state: args.reviewActivityState,
    score: args.reviewActivityScore,
    pullRequest: {
      number: args.pullRequestNumber,
      title: args.pullRequestTitle,
      url: args.pullRequestUrl
    },
    repositoryName: args.repositoryName
  };
}

const meta: Meta<FlatArgs> = {
  component: ReviewActivityCardComponent,
  tags: ['autodocs'],
  args: {
    isLoading: false,
    reviewActivityCreatedAt: dayjs().subtract(4, 'days').toISOString(),
    reviewActivityState: 'CHANGES_REQUESTED',
    reviewActivityScore: 3,
    pullRequestNumber: 100,
    pullRequestTitle: '`Leaderboard`: Custom Sliding Time Window',
    pullRequestUrl: 'https://github.com/ls1intum/Hephaestus/pull/100',
    repositoryName: 'Hephaestus'
  },
  argTypes: {
    isLoading: {
      control: {
        type: 'boolean'
      }
    },
    reviewActivityCreatedAt: {
      control: {
        type: 'date'
      }
    },
    reviewActivityState: {
      options: ['APPROVED', 'CHANGES_REQUESTED', 'COMMENTED'],
      control: {
        type: 'select'
      }
    },
    pullRequestNumber: {
      control: {
        type: 'number'
      }
    },
    pullRequestTitle: {
      control: {
        type: 'text'
      }
    },
    pullRequestUrl: {
      control: {
        type: 'text'
      }
    },
    repositoryName: {
      control: {
        type: 'text'
      }
    }
  }
};

export default meta;

type Story = StoryObj<ReviewActivityCardComponent>;

export const Default: Story = {
  render: (args) => ({
    props: flatArgsToProps(args as unknown as FlatArgs),
    template: `<app-review-activity-card ${argsToTemplate(flatArgsToProps(args as unknown as FlatArgs))} />`
  })
};

export const IsLoading: Story = {
  args: {
    isLoading: true
  },
  render: (args) => ({
    props: flatArgsToProps(args as unknown as FlatArgs),
    template: `<app-review-activity-card ${argsToTemplate(flatArgsToProps(args as unknown as FlatArgs))} />`
  })
};
