import { type Meta, type StoryObj } from '@storybook/angular';
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
    },
    repository: {
      control: {
        type: 'text'
      },
      description: 'Repository to filter for'
    }
  }
};

export default meta;
type Story = StoryObj<LeaderboardFilterComponent>;

export const Default: Story = {
  render: () => ({
    template: '<app-leaderboard-filter />'
  })
};
