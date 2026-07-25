import type { Meta, StoryObj } from "@storybook/react";
import { BudgetExhaustedAlert } from "./BudgetExhaustedAlert";

/**
 * Shown on the Models tab whenever the server reports a live pause for the workspace's current
 * month — without it, a workspace admin browsing models has no signal that detection and the
 * mentor are silently paused (that used to be visible only on the separate usage page).
 *
 * Two axes: *whose* cap tripped (`scope`) — the host's shared-model budget, which the workspace
 * can only route around, or the workspace's own cap on its own provider, which it can lift itself
 * — and *why* (`verdict`): the cap was reached (`EXHAUSTED`), or some usage has no price set so
 * spend can't be checked against it (`UNVERIFIABLE`).
 */
const meta = {
	component: BudgetExhaustedAlert,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: { scope: "own" },
} satisfies Meta<typeof BudgetExhaustedAlert>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The workspace's own cap on its own provider is spent — theirs to raise or remove. */
export const OwnCapExhausted: Story = {
	args: { scope: "own", verdict: "EXHAUSTED" },
};

/**
 * Some calls on the workspace's own models have no price on record, so their own cap can't be
 * enforced — fixable right here by pricing the model.
 */
export const OwnCapUnverifiable: Story = {
	args: { scope: "own", verdict: "UNVERIFIABLE" },
};

/**
 * The host's shared-model budget is spent. Warning, not destructive: the workspace's own provider
 * keeps running, and switching a purpose over is the move they own.
 */
export const SharedBudgetExhausted: Story = {
	args: { scope: "shared", verdict: "EXHAUSTED" },
};

/** Unpriced shared-model usage — only the host can price a shared model, so the copy says so. */
export const SharedBudgetUnverifiable: Story = {
	args: { scope: "shared", verdict: "UNVERIFIABLE" },
};

/** No explicit verdict — defaults to the cap-reached wording. */
export const Default: Story = {};
