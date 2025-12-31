import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { MemberCountModeToggle } from "./MemberCountModeToggle";

const meta = {
	component: MemberCountModeToggle,
	tags: ["autodocs"],
	parameters: {
		layout: "centered",
	},
	argTypes: {
		mode: {
			description: 'Member counting mode: "direct" or "rollup"',
			control: "radio",
			options: ["direct", "rollup"],
		},
		onModeChange: {
			description: "Callback when mode changes",
			action: "mode changed",
		},
	},
	args: {
		onModeChange: fn(),
	},
} satisfies Meta<typeof MemberCountModeToggle>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default state with direct member counting mode selected.
 */
export const Direct: Story = {
	args: {
		mode: "direct",
	},
};

/**
 * Rollup mode selected, showing members from team + all subteams.
 */
export const Rollup: Story = {
	args: {
		mode: "rollup",
	},
};
