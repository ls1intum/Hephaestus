import type { Meta, StoryObj } from "@storybook/react";
import {
	JONAS,
	PRIYA,
	SPARSE_PROFILES,
	TOMAS,
} from "@/components/practices-design/shared/mock-data";
import { CycleFocus, CycleFocusSkeleton } from "./CycleFocus";

/**
 * Candidate C "Focus queue", developer self view.
 *
 * Design intent: at most three things to focus on this cycle, each earned by concrete
 * evidence from the developer's own PRs and issues, each with one next step. Strengths stay
 * visible as a compact affirming line, and the full signal list is one tap away. The bet:
 * a short opinionated list gets acted on, a complete dashboard gets admired and closed.
 *
 * Tradeoffs: the clearest call to action and the least text on screen of the three, but
 * also the most curated: the developer sees the system's selection, not their whole
 * landscape, and has to trust the picker. Works best when observations are high-precision.
 */
const meta = {
	component: CycleFocus,
	tags: ["autodocs"],
	parameters: { layout: "padded" },
} satisfies Meta<typeof CycleFocus>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * A heavy contributor: three focus cards (secrets, validation, error context), each anchored
 * to the PR that earned it, with strengths affirmed below and six more signals one tap away.
 */
export const Filled: Story = {
	args: { profile: PRIYA },
};

/** A clean low-volume developer: no focus needed, strengths affirmed, nothing invented. */
export const NothingToFocusOn: Story = {
	args: { profile: JONAS },
};

/** First cycle: one clear focus from the very first pull request, everything blame-free. */
export const SparseNewWorkspace: Story = {
	args: { profile: TOMAS },
};

/** No activity at all yet: a friendly explanation instead of an empty list. */
export const EmptyNoActivity: Story = {
	args: { profile: SPARSE_PROFILES[1] },
};

/** Workspace totals hidden below the member threshold; the focus list stays complete. */
export const SuppressedTeamContext: Story = {
	args: { profile: PRIYA, teamContextSuppressed: true },
};

/** Loading skeleton mirroring the focus-card layout. */
export const Loading: Story = {
	args: { profile: PRIYA },
	render: () => <CycleFocusSkeleton />,
};
