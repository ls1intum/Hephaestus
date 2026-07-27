import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "storybook/test";
import { expectPageReflows, expectTablesScrollInPlace, expectTargetSize } from "@/test/reflow";
import { AgentJobsTable } from "./AgentJobsTable";
import { mockJobs } from "./story-mock-data";

/**
 * Paginated table of AI runs with a status filter. The Details button in each row is the single
 * affordance that opens the details panel; the table itself exposes no cancel/retry actions.
 */
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

/** Mixed statuses: completed+delivered, running, failed-delivery. */
export const Default: Story = {};

/**
 * The row itself is inert. A click handler on a `<TableRow>` is reachable by mouse only, so the
 * Details button — a real button, in the tab order — is the one way into a run.
 */
export const DetailsButtonIsTheOnlyAffordance: Story = {
	play: async ({ args, canvas }) => {
		// The first cell of the first data row — it holds only the status badge, so clicking it is a
		// click on the row and nothing else. Anchoring on position rather than on a rendered value
		// keeps this about the row being inert, not about what any column happens to display.
		const row = canvas.getAllByRole("row")[1];
		const [statusCell] = within(row).getAllByRole("cell");
		await userEvent.click(statusCell);
		await expect(args.onSelectJob).not.toHaveBeenCalled();

		// "The one way in" is a claim about what the row contains, not just about what a click did:
		// the whole row holds exactly one control, and it is the Details button.
		const controls = within(row).getAllByRole("button");
		await expect(controls).toHaveLength(1);
		await expect(controls[0]).toHaveAccessibleName(/^View details for/);

		await userEvent.click(controls[0]);
		// The run this story rendered into that row, not a fixture read out of the shared module by
		// index — three story files draw on `mockJobs`, and reordering it is not a defect here.
		await expect(args.onSelectJob).toHaveBeenCalledWith(args.jobs[0]);
	},
};

/**
 * The visible "Status" text is the filter's label, so its accessible name is exactly what a
 * speech-control user would say out loud (WCAG SC 2.5.3).
 */
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

/** Filtered to a single status. */
export const FilteredByStatus: Story = {
	args: { statusFilter: "COMPLETED", jobs: mockJobs.filter((j) => j.status === "COMPLETED") },
};

/** A filter is set but no jobs match — the empty state still renders. */
export const FilteredEmpty: Story = {
	args: { statusFilter: "CANCELLED", jobs: [] },
};

/** Query failed — destructive alert with a Retry affordance. */
export const LoadError: Story = {
	args: { isError: true, jobs: [], onRetry: fn() },
};

/**
 * The runs table at the WCAG 2.2 SC 1.4.10 reflow width (320 CSS px).
 *
 * Seven `whitespace-nowrap` columns cannot reflow, so this is the standard's documented data-table
 * exception: the table scrolls horizontally *inside its own bordered container* while the surface
 * around it stays one-dimensional. The row action is also checked against SC 2.5.8's 24x24 px
 * minimum, since icon-only buttons are where that limit is usually missed.
 */
export const MobileReflow: Story = {
	parameters: {
		layout: "fullscreen",
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 375, 768] },
	},
	play: async ({ canvasElement }) => {
		await expectPageReflows();
		// `expectOverflow`: this table really must be wider than its scroller, or "and it scrolls
		// sideways in place" would be a claim about something that never happened.
		await expectTablesScrollInPlace(canvasElement, { expectOverflow: true });

		// Size only, not position: this action legitimately sits off to the right until the table is
		// scrolled to it. That is the sanctioned data-table exception, not an unreachable control.
		await expectTargetSize(
			within(canvasElement).getAllByRole("button", { name: /^View details for/ })[0],
		);
	},
};
