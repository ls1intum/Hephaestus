// biome-ignore assist/source/organizeImports: <explanation>
import { XpProgress } from "@/components/profile/XpProgress";
import type { Meta, StoryObj } from "@storybook/react";

const meta = {
	component: XpProgress,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	argTypes: {
		currentXP: {
			control: { type: "number", min: 0 },
			description: "Current XP earned in the current level",
		},
		xpNeeded: {
			control: { type: "number", min: 1 },
			description: "Total XP needed to reach the next level",
		},
		totalXP: {
			control: { type: "number", min: 0 },
			description: "Total lifetime XP accumulated",
		},
	},
} satisfies Meta<typeof XpProgress>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	args: {
		currentXP: 450,
		xpNeeded: 1000,
		totalXP: 5450,
		className: "w-[300px]",
	},
};

export const JustStarted: Story = {
	args: {
		currentXP: 0,
		xpNeeded: 150,
		totalXP: 0,
		className: "w-[300px]",
	},
};

export const AlmostLevelUp: Story = {
	args: {
		currentXP: 950,
		xpNeeded: 1000,
		totalXP: 10950,
		className: "w-[300px]",
	},
};

export const HighLevel: Story = {
	args: {
		currentXP: 25000,
		xpNeeded: 50000,
		totalXP: 1250000,
		className: "w-[300px]",
	},
};
