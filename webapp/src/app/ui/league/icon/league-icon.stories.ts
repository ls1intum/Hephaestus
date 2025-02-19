import { type Meta, type StoryObj } from '@storybook/angular';
import { LeagueIconComponent } from './league-icon.component';

const meta: Meta<LeagueIconComponent> = {
  component: LeagueIconComponent,
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
type Story = StoryObj<LeagueIconComponent>;

export const NoneIcon: Story = {
  args: {
    leaguePoints: undefined
  }
};

export const BronzeIcon: Story = {
  args: {
    leaguePoints: 1000
  }
};

export const SilverIcon: Story = {
  args: {
    leaguePoints: 1250
  }
};

export const GoldIcon: Story = {
  args: {
    leaguePoints: 1500
  }
};

export const DiamondIcon: Story = {
  args: {
    leaguePoints: 1750
  }
};

export const MasterIcon: Story = {
  args: {
    leaguePoints: 2000
  }
};
