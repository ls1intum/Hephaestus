import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";

import { expectNoOverflowingElement } from "@/stories/reflow";

import { CatalogSummary } from "./CatalogSummary";

const meta = {
	component: CatalogSummary,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		onReviewChanges: fn(),
		counts: { reviewRules: 0, wording: 0, groupAppearance: 0, removedDefaults: 0 },
	},
} satisfies Meta<typeof CatalogSummary>;

export default meta;
type Story = StoryObj<typeof meta>;

export const NothingToReview: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("button", { name: "Review changes" })).toBeNull();
		await expect(canvas.queryByText(/Hephaestus change/u)).toBeNull();
	},
};

export const UpdatesWaiting: Story = {
	args: { counts: { reviewRules: 2, wording: 5, groupAppearance: 1, removedDefaults: 0 } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("2 updates would change review rules")).toBeVisible();
		await expect(canvas.getByText("5 updates would change wording or guidance")).toBeVisible();
		await expect(canvas.getByText("1 update would change group appearance")).toBeVisible();
		await expect(canvas.getByText("8 Hephaestus changes need review")).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Review changes" })).toBeVisible();
	},
};

export const RemovedDefault: Story = {
	args: { counts: { reviewRules: 0, wording: 0, groupAppearance: 0, removedDefaults: 1 } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("1 Hephaestus change needs review")).toBeVisible();
		await expect(canvas.getByText("1 entry is no longer in Hephaestus defaults")).toBeVisible();
	},
};

export const ReviewingChanges: Story = {
	args: { ...UpdatesWaiting.args, reviewing: true },
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("button", { name: "Review changes" })).not.toBeInTheDocument();
	},
};

export const NarrowViewport: Story = {
	args: UpdatesWaiting.args,
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async ({ canvasElement }) => {
		await expectNoOverflowingElement(canvasElement);
	},
};
