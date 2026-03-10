import type { Meta, StoryObj } from "@storybook/react";
import { ReactFlowProvider } from "@xyflow/react";
import { AchievementTooltip } from "@/components/achievements/AchievementTooltip";
import {
	apolloClarity,
	aresConflict,
	asUI,
	hadesSecret,
	hephaestusInit,
	poseidonTrident,
	prometheusLongName,
	zeusThunderbolt,
} from "@/components/achievements/storyMockData";
import { Button } from "@/components/ui/button";

/**
 * Component for displaying achievement tooltips with detailed information.
 * Shows achievement name, tier, description, progress, and unlock date when available.
 *
 * Tooltips use Portals to always render above the skill tree canvas.
 */
const meta = {
	component: AchievementTooltip,
	title: "Achievements/AchievementTooltip",
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component: "Displays tooltips for achievements in digital mythological themes.",
			},
			source: {
				state: "closed",
			},
		},
	},
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<ReactFlowProvider>
				<div className="bg-background px-32 pt-40 pb-40 flex justify-center">
					<Story />
				</div>
			</ReactFlowProvider>
		),
	],
} satisfies Meta<typeof AchievementTooltip>;

export default meta;
type Story = StoryObj<typeof meta>;

const Trigger = (
	<Button variant="outline" className="w-32">
		Hover Me
	</Button>
);

/**
 * Tooltip for a common unlocked achievement.
 */
export const CommonUnlocked: Story = {
	args: {
		achievement: asUI(hephaestusInit),
		open: true,
		children: Trigger,
	},
};

/**
 * Tooltip for a rare locked achievement.
 * Notice the monochromatic border to signify its locked state.
 */
export const RareLocked: Story = {
	args: {
		achievement: asUI({ ...aresConflict, status: "locked" }),
		open: true,
		children: Trigger,
	},
};

/**
 * Tooltip for an epic available achievement with partial progress.
 */
export const EpicAvailable: Story = {
	args: {
		achievement: asUI(apolloClarity),
		open: true,
		children: Trigger,
	},
};

/**
 * Tooltip for a legendary unlocked achievement.
 */
export const LegendaryUnlocked: Story = {
	args: {
		achievement: asUI(poseidonTrident),
		open: true,
		children: Trigger,
	},
};

/**
 * Tooltip for a mythic available achievement.
 */
export const MythicAvailable: Story = {
	args: {
		achievement: asUI(zeusThunderbolt),
		open: true,
		children: Trigger,
	},
};

export const HiddenAchievement: Story = {
	args: {
		achievement: asUI(hadesSecret),
		open: true,
		children: Trigger,
	},
};

export const LongTextOverflow: Story = {
	args: {
		achievement: asUI(prometheusLongName),
		open: true,
		children: Trigger,
	},
};
