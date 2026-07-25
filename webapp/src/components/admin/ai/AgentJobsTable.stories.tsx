import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "storybook/test";
import { expectPageReflows, expectTablesScrollInPlace, expectTargetSize } from "@/test/reflow";
import { AgentJobsTable } from "./AgentJobsTable";
import { mockJobs } from "./storyMockData";

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
		await userEvent.click(
			within(canvas.getAllByRole("row")[1]).getByRole("cell", { name: /—|\$/ }),
		);
		await expect(args.onSelectJob).not.toHaveBeenCalled();

		await userEvent.click(canvas.getAllByRole("button", { name: /^View details for/ })[0]);
		await expect(args.onSelectJob).toHaveBeenCalledWith(mockJobs[0]);
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
		await expectTablesScrollInPlace(canvasElement);
		// The table really is wider than the viewport — otherwise the assertion above proves nothing.
		const container = canvasElement.querySelector<HTMLElement>('[data-slot="table-container"]');
		await expect(container).not.toBeNull();
		if (container == null) return;
		await expect(container.scrollWidth).toBeGreaterThan(container.clientWidth);

		// Size only, not position: this action legitimately sits off to the right until the table is
		// scrolled to it. That is the sanctioned data-table exception, not an unreachable control.
		await expectTargetSize(
			within(canvasElement).getAllByRole("button", { name: /^View details for/ })[0],
		);
	},
};
