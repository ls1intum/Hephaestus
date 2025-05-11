import type { Meta, StoryObj } from '@storybook/react';
import { LeagueIcon } from './LeagueIcon';

const meta: Meta<typeof LeagueIcon> = {
  title: 'Leaderboard/LeagueIcon',
  component: LeagueIcon,
  tags: ['autodocs'],
  argTypes: {
    leaguePoints: { control: 'number' },
    size: {
      control: 'select',
      options: ['sm', 'md', 'lg'],
    },
    showPoints: { control: 'boolean' },
  },
};

export default meta;
type Story = StoryObj<typeof LeagueIcon>;

export const Bronze: Story = {
  args: {
    leaguePoints: 250,
    showPoints: true,
  },
};

export const Silver: Story = {
  args: {
    leaguePoints: 750,
    showPoints: true,
  },
};

export const Gold: Story = {
  args: {
    leaguePoints: 1250,
    showPoints: true,
  },
};

export const Diamond: Story = {
  args: {
    leaguePoints: 1750,
    showPoints: true,
  },
};

export const Master: Story = {
  args: {
    leaguePoints: 2500,
    showPoints: true,
  },
};

export const Small: Story = {
  args: {
    leaguePoints: 1250,
    size: 'sm',
    showPoints: true,
  },
};

export const Large: Story = {
  args: {
    leaguePoints: 1250,
    size: 'lg',
    showPoints: true,
  },
};

export const WithoutPoints: Story = {
  args: {
    leaguePoints: 1250,
    showPoints: false,
  },
};