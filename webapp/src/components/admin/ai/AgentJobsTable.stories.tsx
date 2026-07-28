import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "storybook/test";
import { expectPageReflows, expectTablesScrollInPlace, expectTargetSize } from "@/test/reflow";
import { AgentJobsTable } from "./AgentJobsTable";
import {
	mockJobBackingOff,
	mockJobHeldForUnknownReason,
	mockJobHeldOnBudget,
	mockJobQueued,
	mockJobs,
} from "./story-mock-data";

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

/**
 * Three runs that all read "Queued". Only the status cell's second line says why two of them are not
 * moving, and only the two that are waiting on the clock print a time at all.
 */
export const QueuedHeldAndBackingOff: Story = {
	args: { jobs: [mockJobQueued, mockJobHeldOnBudget, mockJobBackingOff] },
	play: async ({ canvas }) => {
		const [, queued, held, backingOff] = canvas.getAllByRole("row");

		// Claimable now: `availableAt` is required on every run, so an unconditional render would put
		// a stale "due …" on this row too.
		await expect(queued).toHaveAccessibleName(/Queued/);
		await expect(queued).not.toHaveAccessibleName(/held|due/i);

		// The cap that parked it, and when it is next due — without a second badge claiming a status
		// the server's enum (and so the filter above) does not have.
		await expect(held).toHaveAccessibleName(/Held · Over the AI budget · due in \d+ minutes/);
		await expect(backingOff).toHaveAccessibleName(/Backing off · due in \d+ minutes/);
		await expect(backingOff).not.toHaveAccessibleName(/Held/);
	},
};

/** A reason the client has never seen still has to reach the operator in words. */
export const HeldForAnUnknownReason: Story = {
	args: { jobs: [mockJobHeldForUnknownReason] },
	play: async ({ canvas }) => {
		const [, held] = canvas.getAllByRole("row");
		await expect(held).toHaveAccessibleName(/Held · Model unavailable · due in \d+ minutes/);
		await expect(held).not.toHaveAccessibleName(/MODEL_UNAVAILABLE/);
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
