import type { Meta, StoryObj } from '@storybook/react';
import { LeagueIcon } from './LeagueIcon';

const meta: Meta<typeof LeagueIcon> = {
  title: 'Components/League/LeagueIcon',
  component: LeagueIcon,
  tags: ['autodocs'],
  argTypes: {
    leaguePoints: { control: { type: 'number', min: 0, max: 2500 } },
    size: { 
      control: 'select', 
      options: ['sm', 'default', 'lg', 'max', 'full'] 
    },
  }
};

export default meta;
type Story = StoryObj<typeof LeagueIcon>;

export const BronzeLeague: Story = {
  args: {
    leaguePoints: 200,
  },
};

export const SilverLeague: Story = {
  args: {
    leaguePoints: 750,
  },
};

export const GoldLeague: Story = {
  args: {
    leaguePoints: 1200,
  },
};

export const DiamondLeague: Story = {
  args: {
    leaguePoints: 1700,
  },
};

export const MasterLeague: Story = {
  args: {
    leaguePoints: 2100,
  },
};

export const Small: Story = {
  args: {
    leaguePoints: 1200,
    size: 'sm',
  },
};

export const Large: Story = {
  args: {
    leaguePoints: 1200,
    size: 'lg',
  },
};

export const Maximum: Story = {
  args: {
    leaguePoints: 1200,
    size: 'max',
  },
};