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
 * Warn before the wall: how much of a cap is gone, and when this month's pace would reach it.
 *
 * Both consoles render it — the workspace admin watching their own cap and the instance admin
 * watching a workspace's — so the only thing that changes between them is the subject of the
 * sentence, never the figures or their order.
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

/** The workspace's own page: the cap is theirs, so the sentence is in the second person. */
export const OwnProviderCap: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText(/You've used 84% of your provider cap/)).toBeVisible();
	},
};

/** The instance console: the same alert about someone else's cap, named. */
export const NamedSubject: Story = {
	args: { subjectName: "Acme" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText(/Acme has used 84% of its provider cap/)).toBeVisible();
	},
};

/** The shared-model budget rather than the workspace's own provider — a different purse, so named. */
export const SharedModelBudget: Story = {
	args: { scope: "shared", subjectName: "Acme" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText(/shared-model budget/)).toBeVisible();
	},
};

/**
 * Too early in the month, or too little spend, for a pace to mean anything: the alert still reports
 * the share consumed and says nothing at all about when the cap would be reached. A projection is
 * withheld rather than guessed.
 */
export const NoProjection: Story = {
	args: { projection: null },
};

/** The pace stays under the cap: a month-end estimate, but no date it is reached. */
export const PaceStaysUnderCap: Story = {
	args: { projection: { projectedMonthEndUsd: 47.5, reachedOn: null } },
};

/** No display currency configured — every figure is USD only, with no parentheticals. */
export const UsdOnly: Story = {
	args: { fx: null },
};

/** Right at the wall. */
export const AtTheCap: Story = {
	args: {
		percent: 100,
		spendUsd: 50,
		projection: { projectedMonthEndUsd: 74, reachedOn: new Date(Date.UTC(2026, 6, 22)) },
	},
};
