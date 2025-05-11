import type { Meta, StoryObj } from '@storybook/react';
import { LeagueEloCard } from './LeagueEloCard';

const meta: Meta<typeof LeagueEloCard> = {
  title: 'Components/League/LeagueEloCard',
  component: LeagueEloCard,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  argTypes: {
    leaguePoints: { control: { type: 'number', min: 0, max: 2500 } },
  }
};

export default meta;
type Story = StoryObj<typeof LeagueEloCard>;

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
    leaguePoints: 2200,
  },
};