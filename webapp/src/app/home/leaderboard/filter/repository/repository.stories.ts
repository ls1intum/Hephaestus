import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { LeaderboardFilterRepositoryComponent } from './repository.component';

const meta: Meta<LeaderboardFilterRepositoryComponent> = {
  component: LeaderboardFilterRepositoryComponent,
  tags: ['autodocs'],
  argTypes: {
    repositories: {
      control: {
        type: 'object'
      },
      description: 'List of repositories to filter by'
    }
  }
};

export default meta;
type Story = StoryObj<LeaderboardFilterRepositoryComponent>;

export const Default: Story = {
  args: {
    repositories: ['ls1intum/Artemis', 'ls1intum/Athena', 'ls1intum/Hephaestus']
  },
  render: (args) => ({
    props: args,
    template: `<app-leaderboard-filter-repository ${argsToTemplate(args)}/>`
  })
};
