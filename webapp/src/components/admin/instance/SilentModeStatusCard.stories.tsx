import type { Meta, StoryObj } from "@storybook/react";
import { SilentModeStatusCard } from "./SilentModeStatusCard";

const meta = {
	component: SilentModeStatusCard,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		settings: { silentModeEngaged: false },
	},
} satisfies Meta<typeof SilentModeStatusCard>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Delivering: Story = {};

export const Engaged: Story = {
	args: {
		settings: {
			silentModeEngaged: true,
			silentModeReason: "Investigating incident #42",
			silentModeChangedAt: new Date(Date.now() - 2 * 3_600_000),
			silentModeChangedBy: "felixtjdietrich",
		},
	},
};

export const Loading: Story = {
	args: { settings: undefined, isLoading: true },
};
