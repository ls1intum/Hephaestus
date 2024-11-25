import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { LeaderboardFilterComponent } from './filter.component';

const meta: Meta<LeaderboardFilterComponent> = {
  component: LeaderboardFilterComponent,
  tags: ['autodocs'],
  args: {
    teams: ['Artemis', 'Athena', 'Hephaestus', 'Iris', 'Lectures']
  },
  argTypes: {
    teams: {
      control: {
        type: 'object'
      },
      description: 'List of teams'
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

export const SingleTeam: Story = {
  args: {
    teams: ['ls1intum/Artemis']
  },
  render: (args) => ({
    props: args,
    template: `<app-leaderboard-filter ${argsToTemplate(args)} />`
  })
};
