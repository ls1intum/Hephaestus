import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, within } from "storybook/test";
import { AreaMatrix, AreaMatrixSkeleton } from "@/components/practices/AreaMatrix";
import {
	buildLargeRoster,
	HEALTH_FILLED,
	HEALTH_SPARSE,
	ROSTER,
	SPARSE_ROSTER,
} from "@/components/practices/story-data";

/**
 * The mentor view's core: a dense developers-by-areas matrix. One status dot per developer per
 * area, icon-only column headers with tooltips, trend arrows only where they carry signal.
 * Clicking an area header filters the roster; clicking a row opens the drill-down. A legend
 * above the grid spells out the dot vocabulary. Triage order comes from the server and is
 * never a ranking.
 */
const meta = {
	component: AreaMatrix,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		roster: ROSTER,
		health: HEALTH_FILLED,
		onOpenDeveloper: fn(),
	},
} satisfies Meta<typeof AreaMatrix>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Six developers in triage order: Priya carries the attention marker and declining trends. */
export const Filled: Story = {
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Priya Raghavan")).toBeInTheDocument();
		// A row click hands the developer to the drill-down.
		await userEvent.click(canvas.getByRole("button", { name: /Priya Raghavan/ }));
		await expect(args.onOpenDeveloper).toHaveBeenCalledWith(
			expect.objectContaining({ userLogin: "priya-r" }),
		);
	},
};

/** Clicking an area header filters the roster to developers with signal in that area. */
export const AreaFilter: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Aisha Okafor")).toBeInTheDocument();
		// Filter by "Recording decisions and documenting changes": only Jonas has signal there.
		await userEvent.click(
			canvas.getByRole("button", { name: "Filter by Recording decisions and documenting changes" }),
		);
		await expect(canvas.getByText("Jonas Weber")).toBeInTheDocument();
		await expect(canvas.queryByText("Aisha Okafor")).not.toBeInTheDocument();
		// Clicking again clears the filter.
		await userEvent.click(
			canvas.getByRole("button", { name: "Filter by Recording decisions and documenting changes" }),
		);
		await expect(await canvas.findByText("Aisha Okafor")).toBeInTheDocument();
	},
};

/** The scannability test: thirty developers still scan as one screen of dots. */
export const ThirtyDevelopers: Story = {
	args: { roster: buildLargeRoster(30) },
};

/** A fresh workspace: two members, first-cycle signals, mostly no-data health. */
export const SparseNewWorkspace: Story = {
	args: { roster: SPARSE_ROSTER, health: HEALTH_SPARSE },
};

/** Loading skeleton mirroring the matrix layout. */
export const Loading: Story = {
	render: () => <AreaMatrixSkeleton />,
};
