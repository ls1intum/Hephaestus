import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";

import { ChatSkeleton } from "./ChatSkeleton";

const meta = {
	component: ChatSkeleton,
	parameters: { layout: "fullscreen" },
	decorators: [
		(Story) => (
			<div className="h-dvh">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof ChatSkeleton>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The stand-in turns are hidden; a screen reader hears that the conversation loads. */
export const Default: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Loading the conversation…")).toBeVisible();
	},
};
