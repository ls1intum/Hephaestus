import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/test/reflow";
import { PracticeReviewsHeader } from "./PracticeReviewsLayout";

const meta = {
	title: "Admin/Practice reviews/Navigation",
	component: PracticeReviewsHeader,
	parameters: {
		a11y: { test: "error" },
		layout: "fullscreen",
		chromatic: { viewports: [320, 1440] },
		viewport: { defaultViewport: "reflow" },
	},
	args: {
		workspaceSlug: "demo",
		activeSection: "reviews",
	},
	decorators: [withWidePage, withStandardPage],
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeReviewsHeader>;

export default meta;
type Story = StoryObj<typeof meta>;

async function expectCurrentTab(
	canvas: Parameters<NonNullable<Story["play"]>>[0]["canvas"],
	name: string,
) {
	await expect(canvas.getByRole("link", { name })).toHaveAttribute("aria-current", "page");
	await expect(canvas.getAllByRole("link", { current: "page" })).toHaveLength(1);
}

export const ReviewActivity: Story = {
	play: async ({ canvas }) => {
		await expectCurrentTab(canvas, "Review activity");
		await expectNoPageOverflow();
	},
};

export const Findings: Story = {
	args: { activeSection: "findings" },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await expectCurrentTab(canvas, "Findings");
	},
};

export const Delivery: Story = {
	args: { activeSection: "delivery" },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await expectCurrentTab(canvas, "Delivery");
	},
};
