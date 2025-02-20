import { type Meta, type StoryObj } from '@storybook/angular';
import { LeagueEloCardComponent } from './elo-card.component';

const meta: Meta<LeagueEloCardComponent> = {
  component: LeagueEloCardComponent,
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
type Story = StoryObj<LeagueEloCardComponent>;

export const Default: Story = {};
