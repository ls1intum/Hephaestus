import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";

import { withStandardPage } from "@/stories/decorators";

import { COMPLETE_COUNTS, RUNNING_COUNTS } from "./fixtures";
import { ScanCounts } from "./ScanCounts";

const meta = {
	component: ScanCounts,
	decorators: [withStandardPage],
	args: { counts: COMPLETE_COUNTS, recorded: true },
	argTypes: { counts: { control: false } },
	tags: ["autodocs"],
} satisfies Meta<typeof ScanCounts>;

export default meta;
type Story = StoryObj<typeof meta>;

function figure(canvas: HTMLElement, label: string) {
	return within(canvas).getByText(label, { selector: "dt" }).nextElementSibling;
}

export const Default: Story = {
	play: async ({ canvasElement, canvas }) => {
		await expect(figure(canvasElement, "Files")).toHaveTextContent("3");
		await expect(figure(canvasElement, "Practice and file pairs")).toHaveTextContent("6");
		await expect(figure(canvasElement, "Reviewed")).toHaveTextContent("4");
		await expect(figure(canvasElement, "Reused")).toHaveTextContent("1");
		await expect(figure(canvasElement, "Problems")).toHaveTextContent("3 2 open");
		await expect(
			canvas.getByText(/Each file can be reviewed against more than one practice\./u),
		).toBeVisible();
		await expect(canvas.getByText(/As recorded when the scan closed\./u)).toBeVisible();
	},
};

export const TotalsNotKnownYet: Story = {
	args: { counts: RUNNING_COUNTS, recorded: false },
	play: async ({ canvasElement, canvas }) => {
		await expect(figure(canvasElement, "Files")).toHaveTextContent("Not known yet");
		await expect(figure(canvasElement, "Practice and file pairs")).toHaveTextContent(
			"Not known yet",
		);
		await expect(canvas.queryByText(/%/u)).not.toBeInTheDocument();
		await expect(canvas.queryByText(/As recorded/u)).not.toBeInTheDocument();
	},
};

export const Reflow: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
};
