import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { LeaderboardFilterComponent } from './filter.component';

const meta: Meta<LeaderboardFilterComponent> = {
  component: LeaderboardFilterComponent,
  tags: ['autodocs'],
  args: {
    repositories: [
      'ls1intum/Artemis',
      'ls1intum/Athena',
      'ls1intum/Hephaestus',
      'ls1intum/Pyris',
      'ls1intum/Ares2',
      'ls1intum/Aeolus',
      'ls1intum/hades',
      'ls1intum/Apollon',
      'ls1intum/Apollon_standalone'
    ]
  },
  argTypes: {
    repositories: {
      control: {
        type: 'object'
      },
      description: 'List of repositories'
    }
  }
};

export default meta;
type Story = StoryObj<LeaderboardFilterComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `<app-leaderboard-filter ${argsToTemplate(args)} />`
  })
};

export const SingleRepository: Story = {
  args: {
    repositories: ['ls1intum/Artemis']
  },
  render: (args) => ({
    props: args,
    template: `<app-leaderboard-filter ${argsToTemplate(args)} />`
  })
};
