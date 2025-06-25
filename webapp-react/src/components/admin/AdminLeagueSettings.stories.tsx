import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { AdminLeagueSettings } from "./AdminLeagueSettings";

/**
 * Component for managing league settings in admin view
 */
const meta = {
	component: AdminLeagueSettings,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	argTypes: {
		isResetting: {
			control: "boolean",
			description: "Whether leagues are currently being reset",
		},
		onResetLeagues: {
			description: "Function called when the reset leagues button is clicked",
		},
	},
	args: {
		isResetting: false,
		onResetLeagues: fn(),
	},
} satisfies Meta<typeof AdminLeagueSettings>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default state of the league settings component
 */
export const Default: Story = {};

/**
 * Loading state when leagues are being reset
 */
export const Resetting: Story = {
	args: {
		isResetting: true,
	},
};
