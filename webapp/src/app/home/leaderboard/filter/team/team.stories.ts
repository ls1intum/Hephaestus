import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { LeaderboardFilterTeamComponent } from './team.component';

const meta: Meta<LeaderboardFilterTeamComponent> = {
  component: LeaderboardFilterTeamComponent,
  tags: ['autodocs'],
  argTypes: {
    teams: {
      control: {
        type: 'object'
      },
      description: 'List of repositories to filter by'
    }
  }
};

export default meta;
type Story = StoryObj<LeaderboardFilterTeamComponent>;

export const Default: Story = {
  args: {
    teams: ['Artemis', 'Athena', 'Hephaestus']
  },
  render: (args) => ({
    props: args,
    template: `<app-leaderboard-filter-repository ${argsToTemplate(args)}/>`
  })
};
