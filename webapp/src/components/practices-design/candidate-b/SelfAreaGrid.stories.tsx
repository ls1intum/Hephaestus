import type { Meta, StoryObj } from "@storybook/react";
import { PRIYA, SPARSE_PROFILES, TOMAS } from "@/components/practices-design/shared/mock-data";
import { SelfAreaGrid, SelfAreaGridSkeleton } from "./SelfAreaGrid";

/**
 * Candidate B "Area grid + side panel", developer self view.
 *
 * Design intent: the complete practice picture, structured by area identity instead of prose.
 * Each area card is scannable in a second (icon, status chip, one line per practice with a
 * sparkline) and every line expands in place to the evidence and one next step, deep-linking
 * the PR or issue. Quiet areas collapse into a single muted line.
 *
 * Tradeoffs: the most complete self view and the best answer to "structure over text", but
 * activity is referenced rather than lived in: the developer reads about their PRs instead of
 * seeing them as a feed. Pairs naturally with the mentor matrix since both speak the same
 * card and dot language.
 */
const meta = {
	component: SelfAreaGrid,
	tags: ["autodocs"],
	parameters: { layout: "padded" },
} satisfies Meta<typeof SelfAreaGrid>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * A heavy contributor with mixed signals across six areas. Security and error handling carry
 * developing chips with declining trends; pull request craft reads as a strength.
 */
export const Filled: Story = {
	args: { profile: PRIYA },
};

/** First cycle on a fresh workspace: three signals, everything else quiet but not empty boxes. */
export const SparseNewWorkspace: Story = {
	args: { profile: TOMAS },
};

/** No activity at all yet: one friendly explanation instead of twelve empty cards. */
export const EmptyNoActivity: Story = {
	args: { profile: SPARSE_PROFILES[1] },
};

/** Workspace totals hidden below the member threshold; the personal grid stays complete. */
export const SuppressedTeamContext: Story = {
	args: { profile: PRIYA, teamContextSuppressed: true },
};

/** Loading skeleton mirroring the card grid. */
export const Loading: Story = {
	args: { profile: PRIYA },
	render: () => <SelfAreaGridSkeleton />,
};
