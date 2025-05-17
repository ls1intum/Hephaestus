import type { Meta, StoryObj } from '@storybook/react';
import { LeagueIcon } from './LeagueIcon';

/**
 * Dynamic league icon component that displays the appropriate tier icon based on points.
 * Shows different tiers (None, Bronze, Silver, Gold, Diamond, Master) with optional point display.
 */
const meta = {
  title: 'Leaderboard/Components/LeagueIcon',
  component: LeagueIcon,
  tags: ['autodocs'],
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'A component that displays different league tier icons based on point thresholds.',
      },
    },
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
      table: {
        type: { summary: 'number' },
        defaultValue: { summary: 'undefined' },
      },
    },
    size: {
      control: 'inline-radio',
      options: ['sm', 'default', 'lg', 'max', 'full'],
      description: 'Size of the league icon',
      table: {
        type: { summary: 'string' },
        defaultValue: { summary: 'default' },
      },
    },
    showPoints: {
      control: 'boolean',
      description: 'Whether to display the league points below the icon',
      table: {
        type: { summary: 'boolean' },
        defaultValue: { summary: 'false' },
      },
    },
    className: {
      description: 'Additional CSS class names',
      table: {
        type: { summary: 'string' },
      },
    },
  },
} satisfies Meta<typeof LeagueIcon>;

export default meta;
type Story = StoryObj<typeof LeagueIcon>;

/**
 * Default state for users who haven't earned any league points yet.
 * Displays a placeholder icon indicating unranked status.
 */
export const NotRanked: Story = {
  args: {
    leaguePoints: undefined,
  },
  parameters: {
    docs: {
      description: {
        story: 'Displayed when a user has not yet earned any league points.',
      },
    },
  },
};

/**
 * Bronze tier icon for users with 0-499 league points.
 * The entry-level achievement for new contributors.
 */
export const Bronze: Story = {
  args: {
    leaguePoints: 250,
  },
  parameters: {
    docs: {
      description: {
        story: 'Bronze tier (0-499 points) - Entry level achievement.',
      },
    },
  },
};

/**
 * Silver tier icon for users with 500-999 league points.
 * Represents consistent contribution and engagement.
 */
export const Silver: Story = {
  args: {
    leaguePoints: 750,
  },
  parameters: {
    docs: {
      description: {
        story: 'Silver tier (500-999 points) - Regular contributor.',
      },
    },
  },
};

/**
 * Gold tier icon for users with 1000-1499 league points.
 * Represents significant contribution and leadership.
 */
export const Gold: Story = {
  args: {
    leaguePoints: 1250,
  },
  parameters: {
    docs: {
      description: {
        story: 'Gold tier (1000-1499 points) - Significant contributor.',
      },
    },
  },
};

/**
 * Diamond tier icon for users with 1500-1999 league points.
 * Elite status for highly active contributors.
 */
export const Diamond: Story = {
  args: {
    leaguePoints: 1750,
  },
  parameters: {
    docs: {
      description: {
        story: 'Diamond tier (1500-1999 points) - Elite contributor.',
      },
    },
  },
};

/**
 * Master tier icon for users with 2000+ league points.
 * The highest achievement level for exceptional contributors.
 */
export const Master: Story = {
  args: {
    leaguePoints: 2250,
  },
  parameters: {
    docs: {
      description: {
        story: 'Master tier (2000+ points) - Exceptional contributor.',
      },
    },
  },
};

/**
 * Icon with points display to show current progress.
 * Useful for dashboards and user profiles to display exact position.
 */
export const WithPoints: Story = {
  args: {
    leaguePoints: 1500,
    showPoints: true,
  },
  parameters: {
    docs: {
      description: {
        story: 'Shows the exact point count below the league icon.',
      },
    },
  },
};

/**
 * Small size variant for compact UIs and badges.
 * Useful for inline displays with limited space.
 */
export const SmallSize: Story = {
  args: {
    leaguePoints: 1500,
    size: 'sm',
  },
  parameters: {
    docs: {
      description: {
        story: 'Small size variant for compact UI layouts.',
      },
    },
  },
};

/**
 * Large size variant for prominent display.
 * Ideal for profile headers and achievement showcases.
 */
export const LargeSize: Story = {
  args: {
    leaguePoints: 1500,
    size: 'lg',
  },
  parameters: {
    docs: {
      description: {
        story: 'Large size variant for prominent display areas.',
      },
    },
  },
};

/**
 * Complete set of all league tiers shown side by side.
 * Useful for comparison and documentation purposes.
 */
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
  parameters: {
    docs: {
      description: {
        story: 'Visual comparison of all league tiers displayed together with their respective labels.',
      },
    },
  },
};