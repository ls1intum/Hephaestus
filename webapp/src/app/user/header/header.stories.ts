import { argsToTemplate, Meta, StoryObj } from '@storybook/angular';
import dayjs from 'dayjs';
import { UserHeaderComponent } from './header.component';

type FlatArgs = {
  isLoading: boolean;
  avatarUrl: string;
  name: string;
  login: string;
  firstContribution: string;
  contributedRepositories: string;
};

function flatArgsToProps(args: FlatArgs) {
  return {
    isLoading: args.isLoading,
    user: {
      id: 1,
      avatarUrl: args.avatarUrl,
      name: args.name,
      login: args.login,
      htmlUrl: 'https://www.github.com/' + args.login
    },
    firstContribution: dayjs(args.firstContribution),
    contributedRepositories: args.contributedRepositories.split(',').map((repo) => ({
      id: 1,
      name: repo.split('/')[1],
      nameWithOwner: repo,
      htmlUrl: 'https://www.github.com/' + repo
    }))
  };
}

const meta: Meta<FlatArgs> = {
  component: UserHeaderComponent,
  tags: ['autodocs'],
  args: {
    isLoading: false,
    avatarUrl: 'https://avatars.githubusercontent.com/u/11064260?v=4',
    login: 'octocat',
    name: 'Octocat',
    firstContribution: dayjs().subtract(4, 'days').toISOString(),
    contributedRepositories: 'ls1intum/Hephaestus,ls1intum/Artemis,ls1intum/Athena'
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
    contributedRepositories: {
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
