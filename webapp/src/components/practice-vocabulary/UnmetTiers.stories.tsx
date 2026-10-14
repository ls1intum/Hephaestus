import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";

import { expectNoPageOverflow } from "@/stories/reflow";

import { UnmetTiers } from "./UnmetTiers";

const meta = {
	component: UnmetTiers,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: { tiers: ["IN_HOUSE"] },
} satisfies Meta<typeof UnmetTiers>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Not set for In-house members")).toBeVisible();
	},
};

export const SeveralTiers: Story = {
	args: { tiers: ["UNDECLARED", "CLOUD"] },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText("Not set for Cloud members and members who have not chosen"),
		).toBeVisible();
	},
};

export const EveryTier: Story = {
	args: { tiers: ["CLOUD", "UNDECLARED", "IN_HOUSE"] },
	parameters: { viewport: { defaultViewport: "reflow" } },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText("Not set for In-house and Cloud members and members who have not chosen"),
		).toBeVisible();
		await expectNoPageOverflow();
	},
};

export const MissesNoOne: Story = {
	args: { tiers: [] },
	play: async ({ canvas }) => {
		await expect(canvas.queryByText(/^Not set for/u)).toBeNull();
	},
};
