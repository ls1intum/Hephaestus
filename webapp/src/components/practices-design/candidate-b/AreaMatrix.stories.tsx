import type { Meta, StoryObj } from "@storybook/react";
import {
	buildLargeRoster,
	SPARSE_PROFILES,
	TEAM_PROFILES,
	WORKSPACE_HEALTH_FILLED,
	WORKSPACE_HEALTH_SPARSE,
	WORKSPACE_HEALTH_SUPPRESSED,
} from "@/components/practices-design/shared/mock-data";
import { AreaMatrix, AreaMatrixSkeleton } from "./AreaMatrix";

/**
 * Candidate B "Area grid + side panel", mentor view.
 *
 * Design intent: maximum roster scannability. One status dot per developer per area, icon-only
 * column headers, trend arrows only where they carry signal. A mentor sees the whole team's
 * practice landscape in one screen and answers "who needs support in testing" by clicking one
 * column header. Depth lives in the drill-down sheet, where each practice is a single line
 * (name, sparkline, count, status) that expands to evidence with a deep link.
 *
 * Tradeoffs: the strongest triage-at-scale answer and the strongest area filter, but the most
 * abstract of the three. The matrix shows where signal is, not the work itself: activity is
 * always one sheet away rather than inline, and a dot grid can feel like a control panel
 * rather than a coaching tool if the sheet content disappoints.
 */
const meta = {
	component: AreaMatrix,
	tags: ["autodocs"],
	parameters: { layout: "padded" },
} satisfies Meta<typeof AreaMatrix>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Six developers, triage order. Priya's row carries the attention marker and her security and
 * error handling cells show declining trends. Click a row for the drill-down sheet, click an
 * area icon in the header to filter by that area.
 */
export const Filled: Story = {
	args: {
		profiles: TEAM_PROFILES,
		health: WORKSPACE_HEALTH_FILLED,
	},
};

/** The drill-down sheet open on the top needs-attention developer. */
export const DrillDownOpen: Story = {
	args: {
		profiles: TEAM_PROFILES,
		health: WORKSPACE_HEALTH_FILLED,
		initialOpenLogin: "priya-r",
	},
};

/** The real scannability test: thirty developers still fit one screen without scrolling rows. */
export const ThirtyDevelopers: Story = {
	args: {
		profiles: buildLargeRoster(30),
		health: WORKSPACE_HEALTH_FILLED,
	},
};

/** A fresh workspace: two members, one first-cycle signal set, no workspace health yet. */
export const SparseNewWorkspace: Story = {
	args: {
		profiles: SPARSE_PROFILES,
		health: WORKSPACE_HEALTH_SPARSE,
	},
};

/**
 * Workspace health suppressed below the member threshold: the workspace row shows the hidden
 * marker per area while individual rows stay visible to the mentor.
 */
export const SuppressedHealth: Story = {
	args: {
		profiles: TEAM_PROFILES,
		health: WORKSPACE_HEALTH_SUPPRESSED,
	},
};

/** Loading skeleton mirroring the matrix layout. */
export const Loading: Story = {
	args: { profiles: [], health: [] },
	render: () => <AreaMatrixSkeleton />,
};
