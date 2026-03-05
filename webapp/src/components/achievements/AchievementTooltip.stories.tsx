import type { Meta, StoryObj } from "@storybook/react";
import { ReactFlowProvider } from "@xyflow/react";
import { Button } from "@/components/ui/button";
import { AchievementTooltip } from "./AchievementTooltip";
import {
	apolloClarity,
	aresConflict,
	hephaestusInit,
	poseidonTrident,
	zeusThunderbolt,
} from "./storyMockData";

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
				<div
					className="bg-background p-32"
					style={{
						paddingTop: "160px",
						paddingBottom: "160px",
						display: "flex",
						justifyContent: "center",
					}}
				>
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
		achievement: hephaestusInit,
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
		achievement: { ...aresConflict, status: "locked" },
		open: true,
		children: Trigger,
	},
};

/**
 * Tooltip for an epic available achievement with partial progress.
 */
export const EpicAvailable: Story = {
	args: {
		achievement: apolloClarity,
		open: true,
		children: Trigger,
	},
};

/**
 * Tooltip for a legendary unlocked achievement.
 */
export const LegendaryUnlocked: Story = {
	args: {
		achievement: poseidonTrident,
		open: true,
		children: Trigger,
	},
};

/**
 * Tooltip for a mythic available achievement.
 */
export const MythicAvailable: Story = {
	args: {
		achievement: zeusThunderbolt,
		open: true,
		children: Trigger,
	},
};
