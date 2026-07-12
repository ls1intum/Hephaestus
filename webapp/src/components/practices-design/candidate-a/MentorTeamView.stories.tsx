import type { Meta, StoryObj } from "@storybook/react";
import {
	FEEDS_BY_LOGIN,
	SPARSE_FEEDS_BY_LOGIN,
	SPARSE_PROFILES,
	TEAM_PROFILES,
	WORKSPACE_HEALTH_FILLED,
	WORKSPACE_HEALTH_SPARSE,
	WORKSPACE_HEALTH_SUPPRESSED,
} from "@/components/practices-design/shared/mock-data";
import { MentorTeamView, MentorTeamViewSkeleton } from "./MentorTeamView";

/**
 * Candidate A "Activity-anchored feed", mentor view.
 *
 * Design intent: the mentor reads a developer the same way the developer reads themselves,
 * through actual PRs and issues. The left rail is the triage surface (needs-attention
 * developers first, with a one-line why), the right side is the selected developer's real
 * activity with observations attached. The workspace strip on top gives area-level health
 * without naming anyone.
 *
 * Tradeoffs: the deepest single-developer understanding of the three candidates, and zero
 * translation loss between "signal" and "evidence". But cross-team comparison of one area is
 * weak (you visit developers one at a time), and at 30 developers the rail scrolls while the
 * grid of candidate B would still fit one screen.
 */
const meta = {
	component: MentorTeamView,
	tags: ["autodocs"],
	parameters: { layout: "padded" },
} satisfies Meta<typeof MentorTeamView>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Six developers in triage order. Priya opens first with her attention summary in the rail;
 * her feed shows the concrete PRs behind the security and error handling findings.
 */
export const Filled: Story = {
	args: {
		profiles: TEAM_PROFILES,
		feedsByLogin: FEEDS_BY_LOGIN,
		health: WORKSPACE_HEALTH_FILLED,
	},
};

/**
 * A fresh workspace: two members, one with first-cycle activity, no workspace health yet.
 * The rail and feed degrade gracefully instead of showing an intimidating empty matrix.
 */
export const SparseNewWorkspace: Story = {
	args: {
		profiles: SPARSE_PROFILES,
		feedsByLogin: SPARSE_FEEDS_BY_LOGIN,
		health: WORKSPACE_HEALTH_SPARSE,
	},
};

/**
 * Workspace health suppressed below the member threshold: the strip collapses into a single
 * explanatory line while the per-developer work remains fully visible to the mentor.
 */
export const SuppressedHealth: Story = {
	args: {
		profiles: TEAM_PROFILES,
		feedsByLogin: FEEDS_BY_LOGIN,
		health: WORKSPACE_HEALTH_SUPPRESSED,
	},
};

/** Loading skeleton mirroring the rail-plus-feed layout. */
export const Loading: Story = {
	args: {
		profiles: TEAM_PROFILES,
		feedsByLogin: FEEDS_BY_LOGIN,
		health: WORKSPACE_HEALTH_FILLED,
	},
	render: () => <MentorTeamViewSkeleton />,
};
