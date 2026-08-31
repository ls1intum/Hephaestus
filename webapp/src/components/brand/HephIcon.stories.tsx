import type { Meta, StoryObj } from "@storybook/react";

import { HephIcon } from "./HephIcon";

const meta = {
	component: HephIcon,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: { size: 32, animated: true },
	argTypes: { size: { control: { type: "range", min: 12, max: 80, step: 2 } } },
} satisfies Meta<typeof HephIcon>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Streaming: Story = { args: { streaming: true } };

export const Static: Story = { args: { animated: false } };

export const Informative: Story = { args: { label: "Heph, AI mentor" } };
