import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";

import { withStandardPage } from "@/stories/decorators";
import { StatefulPatch } from "@/stories/stateful";

import { PROBLEM_ENTRIES } from "./fixtures";
import { ScanProblemTable } from "./ScanProblemTable";

const ready = (entries = PROBLEM_ENTRIES) => ({
	status: "ready" as const,
	entries,
	hasNext: false,
	loadingMore: false,
	onLoadMore: fn(),
});

const meta = {
	component: ScanProblemTable,
	decorators: [withStandardPage],
	args: {
		state: ready(),
		filter: undefined,
		onFilterChange: fn(),
		changes: { new: 1, unchanged: 1, updated: 1, absent: 2 },
		pairsWithoutResult: 0,
	},
	argTypes: { state: { control: false }, changes: { control: false } },
	render: (args) => (
		<StatefulPatch initial={{ filter: args.filter }}>
			{(value, patch) => (
				<ScanProblemTable
					{...args}
					filter={value.filter}
					onFilterChange={(filter) => {
						patch({ filter });
						args.onFilterChange(filter);
					}}
				/>
			)}
		</StatefulPatch>
	),
	tags: ["autodocs"],
} satisfies Meta<typeof ScanProblemTable>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas, userEvent, args }) => {
		const table = canvas.getByRole("table", { name: "Problems in this scan" });
		await expect(within(table).getByText("New")).toBeVisible();
		await expect(within(table).getByText("Unchanged")).toBeVisible();
		await expect(within(table).getByText("Updated")).toBeVisible();
		await expect(within(table).getByText("Absent")).toBeVisible();
		await expect(within(table).getAllByText("Now met")).toHaveLength(1);
		await expect(within(table).getByText(/That alone does not prove a fix\./u)).toBeVisible();
		await expect(within(table).getAllByText("server/src/ScanService.java:42–48")[0]).toBeVisible();

		await userEvent.click(canvas.getByRole("button", { name: "Absent (2)" }));
		await expect(args.onFilterChange).toHaveBeenCalledWith("ABSENT");
	},
};

export const NoMatches: Story = {
	args: { state: ready([]), filter: "UPDATED" },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByRole("heading", { name: "No problems match this filter" }),
		).toBeVisible();
	},
};

// Withheld pairs prevent a scan with no problems from claiming that all practices were met.
export const NoProblemsWithWithholding: Story = {
	args: {
		state: ready([]),
		changes: { new: 0, unchanged: 0, updated: 0, absent: 0 },
		pairsWithoutResult: 4,
	},
	play: async ({ canvas }) => {
		await expect(
			canvas.getByRole("heading", { name: "This scan recorded no problems" }),
		).toBeVisible();
		await expect(
			canvas.getByText(
				"That does not mean every practice is met: 4 practice and file pairs have no result in this scan.",
			),
		).toBeVisible();
	},
};

export const NotCompared: Story = {
	args: {
		state: ready(PROBLEM_ENTRIES.slice(0, 2).map((entry) => ({ ...entry, baselineState: null }))),
		changes: { new: 0, unchanged: 0, updated: 0, absent: 0 },
	},
	play: async ({ canvas }) => {
		await expect(canvas.getAllByText("Not compared")).toHaveLength(2);
		await expect(canvas.queryByText("Now met")).not.toBeInTheDocument();
	},
};

export const Loading: Story = {
	args: { state: { status: "loading" } },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("table", { name: "Problems in this scan" })).toHaveAttribute(
			"aria-busy",
			"true",
		);
	},
};

export const Error: Story = {
	args: { state: { status: "error", error: { status: 500 }, onRetry: fn() } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("We could not load the problems")).toBeVisible();
	},
};
