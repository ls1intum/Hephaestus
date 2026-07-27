import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "storybook/test";
import { expectPageReflows, expectTablesScrollInPlace, expectTargetSize } from "@/test/reflow";
import { AgentJobsTable } from "./AgentJobsTable";
import { mockJobs } from "./story-mock-data";

/** Paginated table of AI runs with a status filter. Read-only: no cancel or retry lives here. */
const meta = {
	component: AgentJobsTable,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		jobs: mockJobs,
		isLoading: false,
		statusFilter: "ALL",
		onStatusFilterChange: fn(),
		onSelectJob: fn(),
	},
} satisfies Meta<typeof AgentJobsTable>;

export default meta;
type Story = StoryObj<typeof meta>;

export const MixedStatuses: Story = {};

/** A row click handler would be mouse-only, so the Details button is the one way into a run. */
export const DetailsButtonIsTheOnlyAffordance: Story = {
	play: async ({ args, canvas }) => {
		const firstDataRow = within(canvas.getAllByRole("row")[1]);
		const [inertCell] = firstDataRow.getAllByRole("cell");
		await userEvent.click(inertCell);
		await expect(args.onSelectJob).not.toHaveBeenCalled();

		const controls = firstDataRow.getAllByRole("button");
		await expect(controls).toHaveLength(1);
		await expect(controls[0]).toHaveAccessibleName(/^View details for/);

		await userEvent.click(controls[0]);
		await expect(args.onSelectJob).toHaveBeenCalledWith(args.jobs[0]);
	},
};

/** WCAG 2.2 SC 2.5.3: the accessible name is the visible text, so speech control can say it. */
export const FilterIsLabelledByItsVisibleText: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByLabelText("Status")).toBe(
			canvas.getByRole("combobox", { name: "Status" }),
		);
	},
};

export const Loading: Story = {
	args: { isLoading: true },
};

export const Empty: Story = {
	args: { jobs: [] },
};

export const FilteredByStatus: Story = {
	args: { statusFilter: "COMPLETED", jobs: mockJobs.filter((j) => j.status === "COMPLETED") },
};

export const FilteredEmpty: Story = {
	args: { statusFilter: "CANCELLED", jobs: [] },
};

export const LoadError: Story = {
	args: { isError: true, jobs: [], onRetry: fn() },
};

/**
 * WCAG 2.2 SC 1.4.10 at 320 px: the table takes the data-table exception and scrolls inside its own
 * container while the surface around it stays one-dimensional.
 */
export const MobileReflow: Story = {
	parameters: {
		layout: "fullscreen",
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 375, 768] },
	},
	play: async ({ canvasElement }) => {
		await expectPageReflows();
		// `expectOverflow` keeps "and it scrolls sideways in place" from passing on a table that fits.
		await expectTablesScrollInPlace(canvasElement, { expectOverflow: true });

		// Size only: this action sits off to the right until the table is scrolled, which the
		// data-table exception allows.
		await expectTargetSize(
			within(canvasElement).getAllByRole("button", { name: /^View details for/ })[0],
		);
	},
};
