import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";

import { FeedbackModeSourceNote } from "./FeedbackModeSourceNote";

const meta = {
	component: FeedbackModeSourceNote,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: { source: { kind: "inherited", from: "the workspace default" } },
} satisfies Meta<typeof FeedbackModeSourceNote>;

export default meta;
type Story = StoryObj<typeof meta>;

export const FromTheWorkspace: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Follows the workspace default")).toBeVisible();
	},
};

export const FromItsGroup: Story = {
	args: { source: { kind: "inherited", from: "Review-ready work" } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Follows Review-ready work")).toBeVisible();
	},
};

export const ChosenHere: Story = {
	args: { source: { kind: "chosen" } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Set for this practice")).toBeVisible();
	},
};
