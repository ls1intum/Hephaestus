import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import type { FxRateInfo } from "@/api/types.gen";
import { BudgetPaceAlert } from "./BudgetPaceAlert";

const eur: FxRateInfo = {
	currencyCode: "EUR",
	ratePerUsd: 0.88,
	rateDate: "2026-07-24" as unknown as Date,
	source: "ECB",
};

/**
 * Warn before the wall: how much of a cap is gone, and when this month's pace would reach it. Both
 * consoles render it, so only the subject of the sentence changes — never the figures or their order.
 */
const meta = {
	component: BudgetPaceAlert,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		scope: "provider",
		percent: 84,
		spendUsd: 42,
		capUsd: 50,
		projection: {
			projectedMonthEndUsd: 62.4,
			reachedOn: new Date(Date.UTC(2026, 6, 27)),
		},
		fx: eur,
	},
} satisfies Meta<typeof BudgetPaceAlert>;

export default meta;
type Story = StoryObj<typeof meta>;

export const OwnProviderCap: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText(/You've used 84% of your provider cap/)).toBeVisible();
	},
};

export const NamedSubject: Story = {
	args: { subjectName: "Acme" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText(/Acme has used 84% of its provider cap/)).toBeVisible();
	},
};

export const SharedModelBudget: Story = {
	args: { scope: "shared", subjectName: "Acme" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText(/shared-model budget/)).toBeVisible();
	},
};

/** A projection is withheld rather than guessed; the share consumed is still reported. */
export const NoProjection: Story = {
	args: { projection: null },
};

export const PaceStaysUnderCap: Story = {
	args: { projection: { projectedMonthEndUsd: 47.5, reachedOn: null } },
};

export const UsdOnly: Story = {
	args: { fx: null },
};

export const AtTheCap: Story = {
	args: {
		percent: 100,
		spendUsd: 50,
		projection: { projectedMonthEndUsd: 74, reachedOn: new Date(Date.UTC(2026, 6, 22)) },
	},
};
