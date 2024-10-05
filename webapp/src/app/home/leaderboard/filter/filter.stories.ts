import { argsToTemplate, type Meta, type StoryObj } from '@storybook/angular';
import { LeaderboardFilterComponent } from './filter.component';

const meta: Meta<LeaderboardFilterComponent> = {
  component: LeaderboardFilterComponent,
  tags: ['autodocs'],
  argTypes: {
    after: {
      control: {
        type: 'text'
      },
      description: 'Left limit of the timeframe'
    },
    before: {
      control: {
        type: 'text'
      },
      description: 'Right limit of the timeframe'
    }
  }
};

export default meta;
type Story = StoryObj<LeaderboardFilterComponent>;

export const Default: Story = {
  args: {
    after: '2024-09-09',
    before: '2024-09-15'
  },
  render: (args) => ({
    props: args,
    template: `<app-leaderboard-filter ${argsToTemplate(args)} />`
  })
};
