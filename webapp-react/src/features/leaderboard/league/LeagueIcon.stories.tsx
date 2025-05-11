import type { Meta, StoryObj } from '@storybook/react';
import { LeagueIcon } from './LeagueIcon';

const meta: Meta<typeof LeagueIcon> = {
  title: 'Leaderboard/League/LeagueIcon',
  component: LeagueIcon,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
  },
  args: {
    leaguePoints: 1100,
    size: 'default',
    showPoints: false,
  },
  argTypes: {
    leaguePoints: {
      control: {
        type: 'number',
        min: 0,
        max: 3000,
        step: 100,
      },
      description: 'League points used to determine the tier',
    },
    size: {
      control: 'inline-radio',
      options: ['sm', 'default', 'lg', 'max', 'full'],
      description: 'Size of the league icon',
    },
    showPoints: {
      control: 'boolean',
      description: 'Whether to display the league points below the icon',
    },
  },
};

export default meta;
type Story = StoryObj<typeof LeagueIcon>;

export const NotRanked: Story = {
  args: {
    leaguePoints: undefined,
  },
};

export const Bronze: Story = {
  args: {
    leaguePoints: 250,
  },
};

export const Silver: Story = {
  args: {
    leaguePoints: 750,
  },
};

export const Gold: Story = {
  args: {
    leaguePoints: 1250,
  },
};

export const Diamond: Story = {
  args: {
    leaguePoints: 1750,
  },
};

export const Master: Story = {
  args: {
    leaguePoints: 2250,
  },
};

export const WithPoints: Story = {
  args: {
    leaguePoints: 1500,
    showPoints: true,
  },
};

export const SmallSize: Story = {
  args: {
    leaguePoints: 1500,
    size: 'sm',
  },
};

export const LargeSize: Story = {
  args: {
    leaguePoints: 1500,
    size: 'lg',
  },
};

export const AllLeagueTiers: Story = {
  render: () => (
    <div className="flex gap-6 items-end">
      <div className="flex flex-col items-center">
        <LeagueIcon />
        <span className="mt-2 text-xs text-muted-foreground">Not Ranked</span>
      </div>
      <div className="flex flex-col items-center">
        <LeagueIcon leaguePoints={250} />
        <span className="mt-2 text-xs text-muted-foreground">Bronze</span>
      </div>
      <div className="flex flex-col items-center">
        <LeagueIcon leaguePoints={750} />
        <span className="mt-2 text-xs text-muted-foreground">Silver</span>
      </div>
      <div className="flex flex-col items-center">
        <LeagueIcon leaguePoints={1250} />
        <span className="mt-2 text-xs text-muted-foreground">Gold</span>
      </div>
      <div className="flex flex-col items-center">
        <LeagueIcon leaguePoints={1750} />
        <span className="mt-2 text-xs text-muted-foreground">Diamond</span>
      </div>
      <div className="flex flex-col items-center">
        <LeagueIcon leaguePoints={2250} />
        <span className="mt-2 text-xs text-muted-foreground">Master</span>
      </div>
    </div>
  ),
};