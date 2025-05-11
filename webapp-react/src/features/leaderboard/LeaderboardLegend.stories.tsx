import type { Meta, StoryObj } from '@storybook/react';
import { LeaderboardLegend } from './LeaderboardLegend';

const meta: Meta<typeof LeaderboardLegend> = {
  title: 'Leaderboard/LeaderboardLegend',
  component: LeaderboardLegend,
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof LeaderboardLegend>;

export const Default: Story = {};