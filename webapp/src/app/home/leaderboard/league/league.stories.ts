import { type Meta, type StoryObj } from '@storybook/angular';
import { LeaderboardLeagueComponent } from './league.component';

const meta: Meta<LeaderboardLeagueComponent> = {
  component: LeaderboardLeagueComponent,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered'
  },
  args: {
    leaguePoints: 1100
  },
  argTypes: {
    leaguePoints: {
      control: {
        type: 'number'
      },
      description: 'Current League Points to be displayed'
    }
  }
};

export default meta;
type Story = StoryObj<LeaderboardLeagueComponent>;

export const Default: Story = {};
