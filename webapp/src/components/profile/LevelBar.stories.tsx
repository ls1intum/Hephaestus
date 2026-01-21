import type { Meta, StoryObj } from "@storybook/react";
import { XpProgress } from "./XpProgress.tsx";

/**
 * Visual indicator of a user's progression level and experience points (XP).
 * Displays current level, numeric XP progress, and a graphical bar.
 */
const meta = {
	component: XpProgress,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"A progress bar component specialized for displaying user level and experience points.",
			},
		},
	},
	argTypes: {
		level: {
			description: "The current level of the user",
			control: { type: "number", min: 1 },
		},
		currentXP: {
			description: "Current experience points earned in this level",
			control: { type: "number", min: 0 },
		},
		xpNeeded: {
			description: "Total experience points needed to reach the next level",
			control: { type: "number", min: 1 },
		},
		className: {
			description: "Additional CSS classes for styling",
			control: "text",
		},
	},
	tags: ["autodocs"],
} satisfies Meta<typeof XpProgress>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Standard display showing a user in the early stages of a level.
 * Represents a "Neophyte Smith" just starting their journey.
 */
export const Default: Story = {
	args: {
		level: 1,
		currentXP: 150,
		xpNeeded: 1000,
	},
};

/**
 * An edge case where the user has zero experience in the current level.
 */
export const Empty: Story = {
	args: {
		level: 1,
		currentXP: 0,
		xpNeeded: 1000,
	},
};

/**
 * Display showing a user halfway through a level.
 * Represents a "Cyclops Apprentice" gaining momentum.
 */
export const HalfFull: Story = {
	args: {
		level: 5,
		currentXP: 2500,
		xpNeeded: 5000,
	},
};

/**
 * Display showing a user nearly completing a level.
 * Represents a "Technomancer Adept" ready to ascend.
 */
export const AlmostComplete: Story = {
	args: {
		level: 10,
		currentXP: 9800,
		xpNeeded: 10000,
	},
};

/**
 * Display showing a high-level user with a nearly full bar.
 * Represents an "Olympian Architect" with significant experience.
 */
export const HighLevel: Story = {
	args: {
		level: 99,
		currentXP: 99999,
		xpNeeded: 100000,
	},
};
