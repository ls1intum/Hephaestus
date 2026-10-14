import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";

import { withStandardPage } from "@/stories/decorators";

import { COMPLETE_REPORT, METHOD_CHANGED_REPORT, PARTIAL_REPORT } from "./fixtures";
import { ScanPracticeTable } from "./ScanPracticeTable";

const meta = {
	component: ScanPracticeTable,
	decorators: [withStandardPage],
	args: { practices: COMPLETE_REPORT.practices },
	argTypes: { practices: { control: false } },
	tags: ["autodocs"],
} satisfies Meta<typeof ScanPracticeTable>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		const [, first] = canvas.getAllByRole("row");
		if (!first) {
			return;
		}
		await expect(within(first).getByText("Errors are handled where they occur")).toBeVisible();
		await expect(within(first).getByText("Done")).toBeVisible();
		await expect(within(first).getByText("Compared")).toBeVisible();
		await expect(within(first).getByText("2 of 3")).toBeVisible();
	},
};

/** One practice was reviewed under a different method than the scan before. */
export const MethodChanged: Story = {
	args: {
		practices: METHOD_CHANGED_REPORT.practices.map((practice, index) =>
			index === 0 ? { ...practice, comparison: "METHOD_CHANGED" as const } : practice,
		),
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Method changed")).toBeVisible();
	},
};

/** A withheld practice says why, and a listing that did not finish says so. */
export const WithheldAndIncomplete: Story = {
	args: {
		practices: PARTIAL_REPORT.practices.map((practice, index) =>
			index === 0
				? {
						...practice,
						status: "WITHHELD" as const,
						reason: "MODEL_UNAVAILABLE",
						listingComplete: false,
					}
				: practice,
		),
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Withheld")).toBeVisible();
		await expect(canvas.getByText("The assigned AI model was unavailable.")).toBeVisible();
		await expect(canvas.getByText(/Listing incomplete/u)).toBeVisible();
	},
};

export const Empty: Story = {
	args: { practices: [] },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("This scan reached no practice.")).toBeVisible();
	},
};
