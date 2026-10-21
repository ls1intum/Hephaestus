import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";

import { HephTimelineSkeleton } from "./HephTimelineSkeleton";

const meta = {
	component: HephTimelineSkeleton,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
} satisfies Meta<typeof HephTimelineSkeleton>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The stand-in turns are hidden; a screen reader hears that the conversation loads. */
export const Default: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Loading the conversation…")).toBeVisible();
	},
};
