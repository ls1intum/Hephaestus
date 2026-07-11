import type { Meta, StoryObj } from "@storybook/react";
import { expect, within } from "storybook/test";
import type { AreaHealth } from "@/api/types.gen";
import { WorkspaceHealthCard } from "./WorkspaceHealthCard";

const meta = {
	component: WorkspaceHealthCard,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
} satisfies Meta<typeof WorkspaceHealthCard>;

export default meta;
type Story = StoryObj<typeof meta>;

const health = (over: Partial<AreaHealth>): AreaHealth => ({
	areaName: "Clear PR description",
	areaSlug: "clear-pr",
	availability: "AVAILABLE",
	strengthCount: 6,
	developingCount: 5,
	mixedCount: 5,
	noActivityCount: 5,
	...over,
});

export const Default: Story = {
	args: { health: health({}) },
	play: async ({ canvasElement }) => {
		// Not suppressed: the aggregate status labels and counts render.
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Strength")).toBeVisible();
		await expect(canvas.getByText("6")).toBeVisible();
	},
};

export const Suppressed: Story = {
	args: {
		health: {
			areaName: "Reproduce before fixing",
			areaSlug: "repro-first",
			availability: "SUPPRESSED",
		},
	},
	play: async ({ canvasElement }) => {
		// k-anonymity suppression: the practice still renders, but NO status labels or counts do.
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Reproduce before fixing")).toBeVisible();
		await expect(
			canvas.getByText("Not enough activity yet to show without naming anyone."),
		).toBeVisible();
		await expect(canvas.queryByText("No activity in this area yet.")).toBeNull();
		await expect(canvas.queryByText("Strength")).toBeNull();
		await expect(canvas.queryByText("Focus area")).toBeNull();
	},
};

export const NoData: Story = {
	args: {
		health: {
			areaName: "Reproduce before fixing",
			areaSlug: "repro-first",
			availability: "NO_DATA",
		},
	},
	play: async ({ canvasElement }) => {
		// noData is NOT a privacy suppression — it's an honest "nobody active here yet" and must read
		// differently from the Suppressed story's "hidden to protect privacy" message.
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Reproduce before fixing")).toBeVisible();
		await expect(canvas.getByText("No activity in this area yet.")).toBeVisible();
		await expect(
			canvas.queryByText("Not enough activity yet to show without naming anyone."),
		).toBeNull();
		await expect(canvas.queryByText("Strength")).toBeNull();
	},
};

export const LongName: Story = {
	args: {
		health: health({
			areaName: "Write descriptive pull request titles and summaries that explain the change",
		}),
	},
};
