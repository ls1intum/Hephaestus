import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import {
	SPARSE_PROFILES,
	TEAM_PROFILES,
	WORKSPACE_HEALTH_FILLED,
	WORKSPACE_HEALTH_SPARSE,
	WORKSPACE_HEALTH_SUPPRESSED,
} from "@/components/practices-design/shared/mock-data";
import { FocusQueue, FocusQueueSkeleton } from "./FocusQueue";

/**
 * Candidate C "Focus queue", mentor view.
 *
 * Design intent: mentors do not want a dashboard, they want to know what to do today. The
 * queue turns the roster into a short to-do list: one card per developer who could use
 * support, with the why and the concrete evidence (deep-linked PRs and issues) on the card
 * itself. Everyone doing fine is one line each below, so 30 developers collapse into two or
 * three actionable cards. Triage is the interface, not a feature of it.
 *
 * Tradeoffs: the fastest zero-to-action path of the three and the least screen space wasted
 * on the healthy majority. But it hides the practice landscape: no per-area filtering, and a
 * mentor who wants "show me testing across the team" needs the drill-down (or candidate B's
 * matrix). It also leans entirely on the quality of the needs-attention heuristic.
 */
const meta = {
	component: FocusQueue,
	tags: ["autodocs"],
	parameters: { layout: "padded" },
	args: { onOpenDeveloper: fn() },
} satisfies Meta<typeof FocusQueue>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Two developers in the queue: Priya with security, testing and error handling evidence, and
 * Mara with a declining commit hygiene trend. The other four are one line each.
 */
export const Filled: Story = {
	args: {
		profiles: TEAM_PROFILES,
		health: WORKSPACE_HEALTH_FILLED,
	},
};

/** Everyone is doing fine: the queue celebrates instead of inventing work. */
export const EmptyQueue: Story = {
	args: {
		profiles: TEAM_PROFILES.map((profile) => ({
			...profile,
			needsAttention: false,
			attentionSummary: undefined,
		})),
		health: WORKSPACE_HEALTH_FILLED,
	},
};

/** A fresh workspace: two members, no triage signal yet, no workspace totals. */
export const SparseNewWorkspace: Story = {
	args: {
		profiles: SPARSE_PROFILES,
		health: WORKSPACE_HEALTH_SPARSE,
	},
};

/** Workspace totals suppressed below the member threshold, queue still fully usable. */
export const SuppressedHealth: Story = {
	args: {
		profiles: TEAM_PROFILES,
		health: WORKSPACE_HEALTH_SUPPRESSED,
	},
};

/** Loading skeleton mirroring the queue-then-roster layout. */
export const Loading: Story = {
	args: { profiles: [], health: [] },
	render: () => <FocusQueueSkeleton />,
};
