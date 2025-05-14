import type { Meta, StoryObj } from "@storybook/react";
import { LeagueProgressCard } from "./LeagueProgressCard";
import { action } from "@storybook/addon-actions";

const meta: Meta<typeof LeagueProgressCard> = {
  title: "Leaderboard/League/LeagueProgressCard",
  component: LeagueProgressCard,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component: "A component that displays the user's league progress with a progress bar. The progress is calculated based on where the user's points fall between the min and max points of their current league tier."
      }
    }
  },
  decorators: [
    (Story, context) => {
      // Get league info for the current story for debugging
      const { leaguePoints } = context.args;
      const leagues = [
        { name: 'Bronze', minPoints: 0, maxPoints: 1250 },
        { name: 'Silver', minPoints: 1250, maxPoints: 1500 },
        { name: 'Gold', minPoints: 1500, maxPoints: 1750 },
        { name: 'Diamond', minPoints: 1750, maxPoints: 2000 },
        { name: 'Master', minPoints: 2000, maxPoints: Infinity }
      ];
      const currentLeague = leagues.find(
        (league) => leaguePoints >= league.minPoints && leaguePoints < league.maxPoints
      );
      
      const progressValue = currentLeague ? 
        ((leaguePoints - currentLeague.minPoints) * 100) / 
        (currentLeague.maxPoints - currentLeague.minPoints) : 0;
      
      return (
        <div className="min-w-[340px]">
          <Story />
          {context.viewMode === 'docs' && (
            <div className="mt-4 p-4 bg-muted rounded-md text-sm">
              <div><strong>League:</strong> {currentLeague?.name}</div>
              <div><strong>Points:</strong> {leaguePoints}</div>
              <div><strong>Min/Max:</strong> {currentLeague?.minPoints} - {currentLeague?.maxPoints === Infinity ? '∞' : currentLeague?.maxPoints}</div>
              <div><strong>Progress:</strong> {Math.round(progressValue)}%</div>
            </div>
          )}
        </div>
      );
    },
  ],
  argTypes: {
    onInfoClick: {
      description: "Callback when the info button is clicked",
      control: false
    },
    leaguePoints: {
      description: "The current league points of the user",
      control: { type: "range", min: 0, max: 2500, step: 50 }
    }
  }
};

export default meta;

type Story = StoryObj<typeof LeagueProgressCard>;

// Bronze League (0-1250)
export const BronzeStart: Story = {
  args: {
    leaguePoints: 100, // 8% progress in Bronze
    onInfoClick: action("Info button clicked"),
  },
  parameters: {
    docs: {
      description: {
        story: "Bronze league at the beginning (8% progress)"
      }
    }
  }
};

export const BronzeMidway: Story = {
  args: {
    leaguePoints: 625, // 50% progress in Bronze
    onInfoClick: action("Info button clicked"),
  },
  parameters: {
    docs: {
      description: {
        story: "Bronze league at midpoint (50% progress)"
      }
    }
  }
};

export const BronzeNearPromotion: Story = {
  args: {
    leaguePoints: 1150, // 92% progress in Bronze
    onInfoClick: action("Info button clicked"),
  },
  parameters: {
    docs: {
      description: {
        story: "Bronze league near promotion (92% progress)"
      }
    }
  }
};

// Silver League (1250-1500)
export const SilverNew: Story = {
  args: {
    leaguePoints: 1260, // 4% progress in Silver
    onInfoClick: action("Info button clicked"),
  },
  parameters: {
    docs: {
      description: {
        story: "Just promoted to Silver (4% progress)"
      }
    }
  }
};

export const SilverMidway: Story = {
  args: {
    leaguePoints: 1375, // 50% progress in Silver
    onInfoClick: action("Info button clicked"),
  },
  parameters: {
    docs: {
      description: {
        story: "Silver league at midpoint (50% progress)"
      }
    }
  }
};

// Gold League (1500-1750)
export const GoldMidway: Story = {
  args: {
    leaguePoints: 1625, // 50% progress in Gold
    onInfoClick: action("Info button clicked"),
  },
  parameters: {
    docs: {
      description: {
        story: "Gold league at midpoint (50% progress)"
      }
    }
  }
};

// Diamond League (1750-2000)
export const DiamondMidway: Story = {
  args: {
    leaguePoints: 1875, // 50% progress in Diamond
    onInfoClick: action("Info button clicked"),
  },
  parameters: {
    docs: {
      description: {
        story: "Diamond league at midpoint (50% progress)"
      }
    }
  }
};

// Master League (2000+)
export const MasterLeague: Story = {
  args: {
    leaguePoints: 2200, // Master league (infinite max)
    onInfoClick: action("Info button clicked"),
  },
  parameters: {
    docs: {
      description: {
        story: "Master league (no progress bar as it's the highest tier)"
      }
    }
  }
};

export const WithoutInfoButton: Story = {
  args: {
    leaguePoints: 1625, // Gold midway
  },
  parameters: {
    docs: {
      description: {
        story: "Without info button (used when embedding in other components)"
      }
    }
  }
};
