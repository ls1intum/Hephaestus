import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { CuratedCatalogSummary } from "./CuratedCatalogSummary";

const meta = {
	title: "Instance admin/Practice catalog/Summary",
	component: CuratedCatalogSummary,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		summary: {
			total: 49,
			updatesChangingDetection: 0,
			updatesChangingWordingOnly: 0,
			editedHere: 0,
			yours: 0,
			retired: 0,
			noLongerShipped: 0,
		},
	},
} satisfies Meta<typeof CuratedCatalogSummary>;

export default meta;
type Story = StoryObj<typeof meta>;

/** An instance nobody has touched: one sentence, no badges. */
export const NothingHasBeenChanged: Story = {
	play: async ({ canvasElement }) => {
		await expect(
			within(canvasElement).getByText("All 49 practices and areas follow Hephaestus."),
		).toBeVisible();
	},
};

/** The two kinds of waiting update are counted apart, so the cheap ones can be cleared together. */
export const UpdatesWaiting: Story = {
	args: {
		summary: {
			total: 49,
			updatesChangingDetection: 2,
			updatesChangingWordingOnly: 5,
			editedHere: 3,
			yours: 0,
			retired: 0,
			noLongerShipped: 0,
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("2 would change detection")).toBeVisible();
		await expect(canvas.getByText("5 wording only")).toBeVisible();
		await expect(canvas.getByText("49 practices and areas. 39 follow Hephaestus.")).toBeVisible();
	},
};

export const CuratedHeavily: Story = {
	args: {
		summary: {
			total: 60,
			updatesChangingDetection: 1,
			updatesChangingWordingOnly: 0,
			editedHere: 8,
			yours: 11,
			retired: 4,
			noLongerShipped: 2,
		},
	},
};
