import type { Meta, StoryObj } from "@storybook/react";
import {
	PRIYA,
	PRIYA_FEED,
	SPARSE_PROFILES,
	TOMAS,
	TOMAS_FEED,
} from "@/components/practices-design/shared/mock-data";
import { DeveloperActivityView, DeveloperActivityViewSkeleton } from "./DeveloperActivityView";

/**
 * Candidate A "Activity-anchored feed", developer self view.
 *
 * Design intent: reflection lives WITH the work. The developer's home is their own
 * chronological feed of PRs and issues, and practice observations attach inline to the
 * artifact rows they came from. The compact area strip on top answers "where do I stand"
 * in one line and doubles as a feed filter, so status and evidence are one gesture apart.
 *
 * Tradeoffs: strongest answer to "detached from activity", and evidence needs no navigation
 * at all. But the practice picture is implicit: there is no single place that lists every
 * practice with guidance, so a developer who wants the full catalog view has to filter
 * area by area. Quiet practices (like Priya's missing code review activity) only surface
 * via the strip, not the feed.
 */
const meta = {
	component: DeveloperActivityView,
	tags: ["autodocs"],
	parameters: { layout: "padded" },
} satisfies Meta<typeof DeveloperActivityView>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * A heavy contributor with mixed signals. Every chip on an artifact row expands in place to
 * the reasoning and one next step. Note the strip: security and error handling carry
 * attention dots with declining trends, while pull request craft reads as a strength.
 */
export const Filled: Story = {
	args: {
		profile: PRIYA,
		feed: PRIYA_FEED,
	},
};

/**
 * A developer in their first cycle on a fresh workspace: two artifacts, first signals with
 * NEW trends, and most areas still quiet. The view stays useful with almost no data because
 * the feed is real activity, not an empty dashboard.
 */
export const SparseNewWorkspace: Story = {
	args: {
		profile: TOMAS,
		feed: TOMAS_FEED,
	},
};

/** A member who has no activity at all yet sees an explanation instead of an empty grid. */
export const EmptyNoActivity: Story = {
	args: {
		profile: SPARSE_PROFILES[1],
		feed: [],
	},
};

/**
 * Workspace totals are suppressed below the member threshold. The personal view is complete
 * either way; a single quiet line explains why team context is missing, without numbers.
 */
export const SuppressedTeamContext: Story = {
	args: {
		profile: PRIYA,
		feed: PRIYA_FEED,
		teamContextSuppressed: true,
	},
};

/** Loading skeleton mirroring the strip-then-feed layout. */
export const Loading: Story = {
	args: { profile: PRIYA, feed: [] },
	render: () => <DeveloperActivityViewSkeleton />,
};
