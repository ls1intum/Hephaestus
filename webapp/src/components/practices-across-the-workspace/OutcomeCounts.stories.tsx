import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";

import { NO_OUTCOMES, outcomes } from "@/stories/practices-across-the-workspace-story-data";

import { OutcomeCounts, OutcomeCountsSkeleton } from "./OutcomeCounts";

const meta = {
	component: OutcomeCounts,
	tags: ["autodocs"],
	parameters: { layout: "padded" },
	args: { outcomes: outcomes([14, 9, 2, 1]) },
} satisfies Meta<typeof OutcomeCounts>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Every outcome in the registry's order, each with its icon and word. */
export const Default: Story = {
	play: async ({ canvas }) => {
		const items = canvas.getAllByRole("listitem");
		await expect(items.map((each) => each.textContent)).toStrictEqual([
			"Met 14",
			"Not met 9",
			"Does not apply 2",
			"Could not determine 1",
		]);
	},
};

/** A zero stays in its place, muted, so the rows of a table line up. */
export const SomeZero: Story = {
	args: { outcomes: outcomes([3, 0, 0, 1]) },
	play: async ({ canvas }) => {
		await expect(canvas.getAllByRole("listitem")).toHaveLength(4);
	},
};

/** No result at all: one line instead of four zeros. */
export const Empty: Story = {
	args: { outcomes: NO_OUTCOMES },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("No results yet")).toBeVisible();
		await expect(canvas.queryByRole("list")).toBeNull();
	},
};

export const Loading: Story = {
	render: () => <OutcomeCountsSkeleton />,
};

export const Phone: Story = {
	globals: { viewport: { value: "phone390" } },
};

export const Dark: Story = { globals: { theme: "dark" } };
