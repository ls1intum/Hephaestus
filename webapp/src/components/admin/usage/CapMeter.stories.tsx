import type { Meta, StoryObj } from "@storybook/react";
import { expect, within } from "storybook/test";
import { CapMeter } from "./CapMeter";

/**
 * The one cap meter, shared by the workspace's own usage page and the instance console's rollup.
 *
 * It owns what must never diverge between them: the tone mapping (normal, amber from 80%,
 * destructive at a pause or 100%), the clamping past 100%, and the announcement grammar. Layout and
 * the caption underneath belong to each surface, so this renders the bar alone — the box below each
 * story stands in for the caption its caller writes.
 */
const meta = {
	component: CapMeter,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		spendUsd: 12.4,
		capUsd: 50,
		percent: 24.8,
		paused: false,
		label: "Shared-model budget used",
	},
	decorators: [
		(Story) => (
			<div className="max-w-xs">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof CapMeter>;

export default meta;
type Story = StoryObj<typeof meta>;

/** A quarter spent: the bar is the background fact it should be. */
export const Default: Story = {
	play: async ({ canvasElement }) => {
		const meter = within(canvasElement).getByRole("progressbar", {
			name: "Shared-model budget used",
		});
		// Percent first, then the amounts that qualify it — the same sentence on both surfaces.
		await expect(meter).toHaveAttribute("aria-valuetext", "25% used, $12.40 of $50");
	},
};

/** From 80% the bar turns amber, the same threshold that raises the pace warning. */
export const NearCap: Story = {
	args: { spendUsd: 41, capUsd: 50, percent: 82 },
};

/** Reached and holding work back: destructive, whatever the percentage rounds to. */
export const Paused: Story = {
	args: { spendUsd: 50.4, capUsd: 50, percent: 100.8, paused: true },
	play: async ({ canvasElement }) => {
		const meter = within(canvasElement).getByRole("progressbar", {
			name: "Shared-model budget used",
		});
		// The bar clamps at the end of its track; the announcement does not round the overspend away.
		await expect(meter).toHaveAttribute("aria-valuenow", "100");
		await expect(meter).toHaveAttribute("aria-valuetext", "101% used, $50.40 of $50");
	},
};

/**
 * A $0 cap is a supported state, not "no cap": it reads as 100% used and pauses immediately. The
 * meter must not divide by it.
 */
export const ZeroCap: Story = {
	args: { spendUsd: 0, capUsd: 0, percent: 100, paused: true },
	play: async ({ canvasElement }) => {
		await expect(
			within(canvasElement).getByRole("progressbar", { name: "Shared-model budget used" }),
		).toHaveAttribute("aria-valuetext", "100% used, $0 of $0");
	},
};

/** The workspace's other purse, named for its owner so a screen reader never has to guess. */
export const ProviderCap: Story = {
	args: {
		spendUsd: 27.6,
		capUsd: 30,
		percent: 92,
		label: "Provider cap used by Acme",
	},
};
