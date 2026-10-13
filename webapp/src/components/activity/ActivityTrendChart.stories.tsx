import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, waitFor, within } from "storybook/test";

import {
	MONTH_OVERVIEW,
	PREVIOUS_TALLY,
	QUIET_OVERVIEW,
	readyOverview,
	spanOf,
	SPARSE_OVERVIEW,
	YEAR_OVERVIEW,
} from "@/stories/activity-story-data";
import { withProvider, withStandardPage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/stories/reflow";

import { weekStarts } from "./activity-tally";
import { ActivityTrendChart } from "./ActivityTrendChart";

/**
 * A category's figures, then one small chart per kind on one shared scale, then the same numbers as
 * a table. Small multiples rather than one stacked or grouped chart: each kind keeps its own shape,
 * no two state colours touch, and a year of three kinds is never a hundred and fifty slivers.
 */
const meta = {
	component: ActivityTrendChart,
	decorators: [withStandardPage],
	tags: ["autodocs"],
	args: {
		state: readyOverview(MONTH_OVERVIEW, "30d", PREVIOUS_TALLY),
		category: "reviews",
		providerType: "GITHUB",
	},
} satisfies Meta<typeof ActivityTrendChart>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The figure a term names, as the summary row writes it. */
function figure(root: HTMLElement, term: string): string | null | undefined {
	const terms = [...root.querySelectorAll("dt")];
	return terms.find((dt) => dt.textContent === term)?.nextElementSibling?.textContent;
}

export const Default: Story = {
	play: async ({ canvas, canvasElement }) => {
		// Each pull request reviewed counts once, however many verdicts it got.
		await expect(figure(canvasElement, "Reviewed")).toBe("12");
		await expect(figure(canvasElement, "Average per week")).toMatch(/^\d+\.\d$/u);
		await expect(figure(canvasElement, "Busiest week")).toMatch(/^\d+ in .+ \(UTC\)$/u);
		await expect(figure(canvasElement, "vs the previous 30 days")).toBe("−3");
		const chart = canvas.getByRole("figure");
		await expect(canvas.getByText("Weeks use UTC.")).toBeVisible();
		await expect(chart).toHaveAccessibleName(/^12 pull requests reviewed\. Busiest week .+, \d+$/u);
		// One chart per kind, each headed by its name and total.
		const rows = within(chart).getAllByRole("listitem");
		await expect(rows.map((row) => row.firstElementChild?.textContent)).toStrictEqual([
			"Approved11",
			"Changes requested3",
			"Commented4",
		]);
		await expect(canvasElement.querySelectorAll("[data-slot=chart]")).toHaveLength(3);
	},
};

/** The table twin: every week's count per kind. */
export const AsTable: Story = {
	args: { state: readyOverview(SPARSE_OVERVIEW, "30d"), category: "pull-requests" },
	play: async ({ canvas, canvasElement, userEvent }) => {
		await expect(figure(canvasElement, "Busiest week")).toMatch(/^2/u);
		const chart = canvas.getByRole("figure");
		const kindRows = within(chart).getAllByRole("listitem");
		await expect(kindRows.map((row) => row.firstElementChild?.textContent)).toStrictEqual([
			"Opened0",
			"Merged3",
			"Closed0",
		]);
		await expect(canvas.queryAllByText("None in this range")).toHaveLength(0);
		const [openedRow, mergedRow, closedRow] = kindRows;
		if (!openedRow || !mergedRow || !closedRow) {
			throw new Error("All pull request kinds remain in the chart");
		}
		await expect(openedRow.getBoundingClientRect().height).toBeLessThan(48);
		await expect(closedRow.getBoundingClientRect().height).toBeLessThan(48);
		await expect(within(mergedRow).getAllByText(/^\d+ \p{L}+(?: \d{4})?$/u).length).toBeGreaterThan(
			0,
		);
		await userEvent.click(canvas.getByRole("button", { name: "Show as table" }));
		const table = canvas.getByRole("table", { name: "Pull requests by week" });
		const [header, ...rows] = within(table).getAllByRole("row");
		// Opened, merged and closed are steps of one lifecycle: they add up to no total.
		await expect(
			within(header ?? table)
				.getAllByRole("columnheader")
				.map((cell) => cell.textContent),
		).toStrictEqual(["Week", "Opened", "Merged", "Closed"]);
		await expect(rows).toHaveLength(weekStarts(spanOf("30d").from, spanOf("30d").to).length);
		// A zero stays in its cell, stepped back, so the counts that are there stand out.
		const [firstWeek] = rows;
		await expect(within(firstWeek ?? table).getAllByRole("cell")[2]).toHaveClass(
			"text-muted-foreground",
		);
		const merged = rows.map((row) => within(row).getAllByRole("cell")[2]?.textContent);
		await expect(merged.filter((count) => count !== "0")).toStrictEqual(["1", "2"]);
	},
};

/** Reviews count pull requests, not verdicts, so their table carries its own column for them. */
export const ReviewsAsTable: Story = {
	decorators: [
		(Story) => (
			<div className="max-w-xs">
				<Story />
			</div>
		),
	],
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Show as table" }));
		const table = canvas.getByRole("table", { name: "Reviews by week" });
		await expectNoPageOverflow();
		const weekHeader = within(table).getByRole("columnheader", { name: "Week" });
		await expect(within(table).getByRole("columnheader", { name: "Reviewed" })).toBeVisible();
		const scroller = table.closest<HTMLElement>("[data-slot='table-container']");
		const firstWeek = within(table).getAllByRole("row")[1];
		if (!scroller || !firstWeek) {
			throw new Error("The weekly table needs its scroll region and first week");
		}
		const cells = within(firstWeek).getAllByRole("cell");
		const week = cells[0];
		const reviewed = cells.at(-1);
		if (!week || !reviewed) {
			throw new Error("The week and reviewed count remain in the row");
		}
		const dateLabel = week.textContent;
		await expect(scroller.scrollWidth).toBeGreaterThan(scroller.clientWidth);
		await userEvent.tab();
		await expect(scroller).toHaveFocus();
		scroller.scrollLeft = scroller.scrollWidth;
		await waitFor(() => {
			const left = scroller.getBoundingClientRect().left + scroller.clientLeft;
			void expect(scroller.scrollLeft).toBeGreaterThan(0);
			void expect(Math.abs(week.getBoundingClientRect().left - left)).toBeLessThanOrEqual(1);
			void expect(Math.abs(weekHeader.getBoundingClientRect().left - left)).toBeLessThanOrEqual(1);
			void expect(week.getBoundingClientRect().width).toBeLessThanOrEqual(130);
			void expect(reviewed.getBoundingClientRect().left).toBeGreaterThanOrEqual(
				week.getBoundingClientRect().right - 1,
			);
			void expect(reviewed.getBoundingClientRect().right).toBeLessThanOrEqual(
				left + scroller.clientWidth + 1,
			);
		});
		await expect(week).toHaveTextContent(dateLabel);
		await expect(reviewed).toHaveTextContent(
			String(MONTH_OVERVIEW.weeks[0]?.tally.pullRequestsReviewed),
		);
	},
};

export const WideReviewsAsTable: Story = {
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Show as table" }));
		const table = canvas.getByRole("table", { name: "Reviews by week" });
		const fullWeek = within(table).getAllByRole("row")[2];
		if (!fullWeek) {
			throw new Error("The review table has no full week");
		}
		const week = within(fullWeek).getAllByRole("cell")[0];
		const label = week?.firstElementChild;
		if (!(label instanceof HTMLElement)) {
			throw new Error("The full week has no visible date label");
		}
		await expect(label).toHaveTextContent("(UTC)");
		await expect(label.getBoundingClientRect().width).toBeGreaterThan(112);
		await expect(label.getBoundingClientRect().height).toBeLessThanOrEqual(
			Number.parseFloat(getComputedStyle(label).lineHeight) + 1,
		);
		await expect(label.scrollWidth).toBeLessThanOrEqual(label.clientWidth + 1);
	},
};

/** Two kinds of one neutral tone: each in its own row, in the accent, never grey. */
export const Comments: Story = { args: { category: "comments" } };

export const TwelveMonths: Story = {
	args: {
		state: readyOverview(YEAR_OVERVIEW, "1y"),
		category: "pull-requests",
	},
	play: async ({ canvas, canvasElement }) => {
		await expect(figure(canvasElement, "Average per week")).toMatch(/^\d+\.\d$/u);
		// A year of weeks starts with its year, so September never reads twice.
		const [firstTick] = canvasElement.querySelectorAll(".recharts-xAxis-tick-labels text");
		await expect(firstTick?.textContent).toMatch(/^\d+ \p{L}+ \d{4}$/u);
		await expect(canvas.getByRole("figure")).toHaveAccessibleName(/Busiest week /u);
	},
};

export const Empty: Story = {
	args: { state: readyOverview(QUIET_OVERVIEW, "30d") },
	play: async ({ canvas, canvasElement, userEvent }) => {
		const chart = canvas.getByRole("figure");
		await expect(chart).toHaveAccessibleName("0 pull requests reviewed");
		await expect(figure(canvasElement, "Busiest week")).toBeUndefined();
		await expect(within(chart).getAllByText("None in this range")).toHaveLength(1);
		await expect(
			within(chart)
				.getAllByRole("listitem")
				.map((row) => row.textContent),
		).toStrictEqual(["Approved0", "Changes requested0", "Commented0"]);
		await userEvent.click(canvas.getByRole("button", { name: "Show as table" }));
		const table = canvas.getByRole("table", { name: "Reviews by week" });
		const [header, ...rows] = within(table).getAllByRole("row");
		await expect(
			within(header ?? table)
				.getAllByRole("columnheader")
				.map((cell) => cell.textContent),
		).toStrictEqual(["Week", "Approved", "Changes requested", "Commented", "Reviewed"]);
		await expect(rows).toHaveLength(QUIET_OVERVIEW.weeks.length);
		await expect(
			rows.flatMap((row) =>
				within(row)
					.getAllByRole("cell")
					.slice(1)
					.map((cell) => cell.textContent),
			),
		).toStrictEqual(Array.from({ length: QUIET_OVERVIEW.weeks.length * 4 }, () => "0"));
	},
};

export const GitLab: Story = {
	decorators: [withProvider("GITLAB")],
	args: { providerType: "GITLAB", category: "pull-requests" },
};

export const Dark: Story = { globals: { theme: "dark" } };

export const Reflow: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async () => {
		await expectNoPageOverflow();
	},
};

export const Loading: Story = {
	args: { state: { status: "loading" } },
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("figure")).not.toBeInTheDocument();
	},
};

const onRetry = fn();

export const Failed: Story = {
	args: { state: { status: "error", error: new Error("Network down"), onRetry } },
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: /Retry/u }));
		await expect(onRetry).toHaveBeenCalledOnce();
	},
};
