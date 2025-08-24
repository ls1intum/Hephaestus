import type { Meta, StoryObj } from "@storybook/react";
import {
	LeagueBronzeIcon,
	LeagueDiamondIcon,
	LeagueGoldIcon,
	LeagueMasterIcon,
	LeagueNoneIcon,
	LeagueSilverIcon,
} from "./LeagueIcons";

/**
 * League icons that represent different achievement tiers in the gamification system.
 * Icons progress from None to Bronze, Silver, Gold, Diamond, and Master as users earn more points.
 */
const meta = {
	tags: ["autodocs"],
	parameters: {
		layout: "centered",
	},
	args: {
		size: "default",
	},
	argTypes: {
		size: {
			control: "inline-radio",
			options: ["sm", "default", "lg", "max", "full"],
			description: "Size of the league icon",
		},
	},
} satisfies Meta<typeof LeagueBronzeIcon>;

export default meta;

/**
 * Bronze tier icon - first achievement tier for new contributors.
 */
export const Bronze: StoryObj<typeof LeagueBronzeIcon> = {
	render: ({ size }) => <LeagueBronzeIcon size={size} />,
};

/**
 * Silver tier icon - represents consistent contribution and engagement.
 */
export const Silver: StoryObj<typeof LeagueSilverIcon> = {
	render: ({ size }) => <LeagueSilverIcon size={size} />,
};

/**
 * Gold tier icon - indicates high-quality and regular contributions.
 */
export const Gold: StoryObj<typeof LeagueGoldIcon> = {
	render: ({ size }) => <LeagueGoldIcon size={size} />,
};

/**
 * Diamond tier icon - represents exceptional contributions and expertise.
 */
export const Diamond: StoryObj<typeof LeagueDiamondIcon> = {
	render: ({ size }) => <LeagueDiamondIcon size={size} />,
};

/**
 * Master tier icon - highest achievement level for top contributors.
 */
export const Master: StoryObj<typeof LeagueMasterIcon> = {
	render: ({ size }) => <LeagueMasterIcon size={size} />,
};

/**
 * None tier icon - shown for users who haven't yet contributed or earned points.
 */
export const None: StoryObj<typeof LeagueNoneIcon> = {
	render: ({ size }) => <LeagueNoneIcon size={size} />,
};

/**
 * Comparison of available size options for league icons.
 */
export const Sizes: StoryObj = {
	render: () => (
		<div className="flex gap-8">
			<div>
				<LeagueGoldIcon size="sm" />
				<div className="mt-2 text-xs text-center text-muted-foreground">
					Small
				</div>
			</div>
			<div>
				<LeagueGoldIcon size="default" />
				<div className="mt-2 text-xs text-center text-muted-foreground">
					Default
				</div>
			</div>
			<div>
				<LeagueGoldIcon size="lg" />
				<div className="mt-2 text-xs text-center text-muted-foreground">
					Large
				</div>
			</div>
			<div>
				<LeagueGoldIcon size="max" />
				<div className="mt-2 text-xs text-center text-muted-foreground">
					Max
				</div>
			</div>
			<div className="w-76">
				<LeagueGoldIcon size="full" />
				<div className="mt-2 text-xs text-center text-muted-foreground">
					Full
				</div>
			</div>
		</div>
	),
};
