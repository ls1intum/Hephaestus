import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";

import { bundledGuidance } from "@/stories/practice-guidance-story-mock-data";
import { largestLabelSize } from "@/test/dom";

import { PracticeGuideMarkdown } from "./PracticeGuideMarkdown";

/**
 * The practice guide, as a practice's Guide tab and the admin previews show it. Its headings sit
 * under the surface's own, so each renders as an `h4`; its one image form is the guide's own figure,
 * drawn bare at the introduction's width, so its labels are the size of the guide's text.
 */
const meta = {
	component: PracticeGuideMarkdown,
	parameters: { layout: "padded" },
	args: { guide: bundledGuidance.guide },
	argTypes: {
		// Markdown and its figures as one record: a text box per figure would let them disagree.
		guide: { control: false },
	},
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeGuideMarkdown>;

export default meta;
type Story = StoryObj<typeof meta>;
type StoryCanvas = Parameters<NonNullable<Story["play"]>>[0]["canvas"];

/**
 * The figure stays in the guide's column and stops at 40 of its ems, so its largest labels are no
 * larger than the guide's text.
 */
async function expectFigureAtTextSize(canvas: StoryCanvas) {
	const figure = canvas.getByRole("img");
	for (const paragraph of canvas.getAllByRole("paragraph")) {
		const textSize = Number.parseFloat(getComputedStyle(paragraph).fontSize);
		const column = paragraph.getBoundingClientRect().width;
		await expect(figure.getBoundingClientRect().width).toBeLessThanOrEqual(
			Math.min(column, 40 * textSize),
		);
		await expect(largestLabelSize(figure)).toBeLessThanOrEqual(textSize);
	}
}

/** The bundled guide, with its figure drawn where the Markdown names it. */
export const Default: Story = {
	play: async ({ canvas }) => {
		await expectFigureAtTextSize(canvas);
	},
};

export const Dark: Story = { globals: { theme: "dark" } };

/** At 320px the figure scales down with the column; nothing leaves it. */
export const MobileReflow: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async ({ canvas, canvasElement }) => {
		await expectFigureAtTextSize(canvas);
		const root = canvasElement.firstElementChild ?? canvasElement;
		await expect(root.scrollWidth).toBeLessThanOrEqual(root.clientWidth);
	},
};
