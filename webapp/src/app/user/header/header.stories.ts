import { argsToTemplate, Meta, StoryObj } from '@storybook/angular';
import dayjs from 'dayjs';
import { UserHeaderComponent } from './header.component';

type FlatArgs = {
  isLoading: boolean;
  avatarUrl: string;
  login: string;
  firstContribution: string;
  repositories: string;
};

function flatArgsToProps(args: FlatArgs) {
  return {
    isLoading: args.isLoading,
    userData: {
      avatarUrl: args.avatarUrl,
      login: args.login,
      firstContribution: dayjs(args.firstContribution),
      repositories: new Set(args.repositories.split(',').map((repo) => repo.trim()))
    }
  };
}

const meta: Meta<FlatArgs> = {
  component: UserHeaderComponent,
  tags: ['autodocs'],
  args: {
    isLoading: false,
    avatarUrl: 'https://avatars.githubusercontent.com/u/11064260?v=4',
    login: 'octocat',
    firstContribution: dayjs().subtract(4, 'days').toISOString(),
    repositories: 'ls1intum/Hephaestus, ls1intum/Artemis, ls1intum/Athena'
  },
  argTypes: {
    isLoading: {
      control: {
        type: 'boolean'
      }
    },
    firstContribution: {
      control: {
        type: 'date'
      }
    },
    avatarUrl: {
      control: {
        type: 'text'
      }
    },
    login: {
      control: {
        type: 'text'
      }
    },
    repositories: {
      control: {
        type: 'text'
      }
    }
  }
};

export default meta;

type Story = StoryObj<UserHeaderComponent>;

export const Default: Story = {
  render: (args) => ({
    props: flatArgsToProps(args as unknown as FlatArgs),
    template: `<app-user-header ${argsToTemplate(flatArgsToProps(args as unknown as FlatArgs))} />`
  })
};

export const IsLoading: Story = {
  args: {
    isLoading: true
  },
  render: (args) => ({
    props: flatArgsToProps(args as unknown as FlatArgs),
    template: `<app-user-header ${argsToTemplate(flatArgsToProps(args as unknown as FlatArgs))} />`
  })
};
