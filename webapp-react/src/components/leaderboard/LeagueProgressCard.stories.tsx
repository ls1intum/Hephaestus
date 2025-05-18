import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { LeagueProgressCard } from "./LeagueProgressCard";

/**
 * Card component that visualizes a user's progress within their current league tier.
 * Shows current tier, points, and progress toward the next tier.
 */
const meta: Meta<typeof LeagueProgressCard> = {
	component: LeagueProgressCard,
	tags: ["autodocs"],
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"A component that displays the user's league progress with a progress bar. The progress is calculated based on where the user's points fall between the min and max points of their current league tier.",
			},
		},
	},
	decorators: [
		(Story: any, context: any) => {
			// Get league info for the current story for debugging
			const { leaguePoints } = context.args;
			const leagues = [
				{ name: "Bronze", minPoints: 0, maxPoints: 1250 },
				{ name: "Silver", minPoints: 1250, maxPoints: 1500 },
				{ name: "Gold", minPoints: 1500, maxPoints: 1750 },
				{ name: "Diamond", minPoints: 1750, maxPoints: 2000 },
				{
					name: "Master",
					minPoints: 2000,
					maxPoints: Number.POSITIVE_INFINITY,
				},
			];
			const currentLeague = leagues.find(
				(league) =>
					leaguePoints >= league.minPoints && leaguePoints < league.maxPoints,
			);

			const progressValue = currentLeague
				? ((leaguePoints - currentLeague.minPoints) * 100) /
					(currentLeague.maxPoints - currentLeague.minPoints)
				: 0;

			return (
				<div className="min-w-[340px]">
					<Story />
					{context.viewMode === "docs" && (
						<div className="mt-4 p-4 bg-muted rounded-md text-sm">
							<div>
								<strong>League:</strong> {currentLeague?.name}
							</div>
							<div>
								<strong>Points:</strong> {leaguePoints}
							</div>
							<div>
								<strong>Min/Max:</strong> {currentLeague?.minPoints} -{" "}
								{currentLeague?.maxPoints === Number.POSITIVE_INFINITY
									? "∞"
									: currentLeague?.maxPoints}
							</div>
							<div>
								<strong>Progress:</strong> {Math.round(progressValue)}%
							</div>
						</div>
					)}
				</div>
			);
		},
	],
	argTypes: {
		onInfoClick: {
			description: "Callback when the info button is clicked",
			control: false,
		},
		leaguePoints: {
			description: "The current league points of the user",
			control: { type: "range", min: 0, max: 2500, step: 50 },
		},
	},
};

export default meta;

type Story = StoryObj<typeof meta>;

/**
 * Early Bronze tier progress - just starting out with few points.
 */
export const BronzeStart: Story = {
	args: {
		leaguePoints: 100, // 8% progress in Bronze
		onInfoClick: fn(),
	},
	parameters: {
		docs: {
			description: {
				story: "Bronze league at the beginning (8% progress)",
			},
		},
	},
};

/**
 * Mid Bronze tier progress with 50% completion toward Silver tier.
 */
export const BronzeMidway: Story = {
	args: {
		leaguePoints: 625, // 50% progress in Bronze
		onInfoClick: fn(),
	},
	parameters: {
		docs: {
			description: {
				story: "Bronze league at midpoint (50% progress)",
			},
		},
	},
};

/**
 * Almost Silver tier - approaching promotion to the next league level.
 */
export const BronzeNearPromotion: Story = {
	args: {
		leaguePoints: 1150, // 92% progress in Bronze
		onInfoClick: fn(),
	},
	parameters: {
		docs: {
			description: {
				story: "Bronze league near promotion (92% progress)",
			},
		},
	},
};

/**
 * Just promoted to Silver tier - showing early progress in a new league.
 */
export const SilverNew: Story = {
	args: {
		leaguePoints: 1260, // 4% progress in Silver
		onInfoClick: fn(),
	},
	parameters: {
		docs: {
			description: {
				story: "Just promoted to Silver (4% progress)",
			},
		},
	},
};

/**
 * Silver tier at 50% progress - steady advancement through the league.
 */
export const SilverMidway: Story = {
	args: {
		leaguePoints: 1375, // 50% progress in Silver
		onInfoClick: fn(),
	},
	parameters: {
		docs: {
			description: {
				story: "Silver league at midpoint (50% progress)",
			},
		},
	},
};

/**
 * Gold tier progress - shows advanced user with significant contributions.
 */
export const GoldMidway: Story = {
	args: {
		leaguePoints: 1625, // 50% progress in Gold
		onInfoClick: fn(),
	},
	parameters: {
		docs: {
			description: {
				story: "Gold league at midpoint (50% progress)",
			},
		},
	},
};

/**
 * Diamond tier progress - represents exceptional contributor approaching elite status.
 */
export const DiamondMidway: Story = {
	args: {
		leaguePoints: 1875, // 50% progress in Diamond
		onInfoClick: fn(),
	},
	parameters: {
		docs: {
			description: {
				story: "Diamond league at midpoint (50% progress)",
			},
		},
	},
};

/**
 * Master tier - highest achievement level with no upper limit.
 * For top contributors who have demonstrated sustained excellence.
 */
export const MasterLeague: Story = {
	args: {
		leaguePoints: 2200, // Master league (infinite max)
		onInfoClick: fn(),
	},
	parameters: {
		docs: {
			description: {
				story: "Master league (no progress bar as it's the highest tier)",
			},
		},
	},
};

/**
 * Variant without info button for embedded use in other components.
 * Shows how the component appears when no additional information is needed.
 */
export const WithoutInfoButton: Story = {
	args: {
		leaguePoints: 1625, // Gold midway
	},
	parameters: {
		docs: {
			description: {
				story: "Without info button (used when embedding in other components)",
			},
		},
	},
};
