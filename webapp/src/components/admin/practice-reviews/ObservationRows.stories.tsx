import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";

import type { PracticeReviewObservationRow } from "@/api/types.gen";
import { expectNoPageOverflow } from "@/stories/reflow";

import { reviewObservations } from "./fixtures";
import { ObservationRows } from "./ObservationRows";

const [first] = reviewObservations;
if (!first) {
	throw new Error("The review fixtures must contain an observation");
}

const other = reviewObservations.find((row) => row.practiceName !== first.practiceName);
if (!other) {
	throw new Error("The review fixtures must contain problems of two practices");
}

function hiddenRow(row: PracticeReviewObservationRow, id: string): PracticeReviewObservationRow {
	return {
		...row,
		id,
		summary: undefined,
		path: undefined,
		startLine: undefined,
		endLine: undefined,
		evidenceHidden: true,
	};
}

const clearFilters = fn();
const retry = fn();

const TABLE = { name: "Problems" } as const;

/**
 * The Observations stream: one row per problem, by day, its severity as the row's icon, what it says
 * with its practice and place, the work and the developer. The row opens the result it belongs to.
 */
const meta = {
	component: ObservationRows,
	parameters: {
		layout: "padded",
		chromatic: { viewports: [320, 768, 1440] },
	},
	tags: ["autodocs"],
	args: {
		state: { status: "ready", observations: reviewObservations },
	},
} satisfies Meta<typeof ObservationRows>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Ready: Story = {
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table", TABLE);
		await expect(within(table).getAllByRole("link", { name: /^Open result/u })).toHaveLength(
			reviewObservations.length,
		);
		within(table).getByText(
			"server/application/src/main/java/de/tum/cit/aet/hephaestus/practices/reviewoutput/ReviewQueryService.java:116–119",
		);
		await expectNoPageOverflow();
	},
};

const code = reviewObservations.find((row) => row.sourceKind === "scm.pull-request.diff");
const codePath = code?.path;
if (!code || codePath === undefined) {
	throw new Error("The review fixtures must contain a problem cited on the diff");
}

/**
 * A place is named as the result's sheet names it: a file and its lines for code, with the side of
 * the diff it is on, and the object's name for anything else.
 */
export const Places: Story = {
	args: {
		state: {
			status: "ready",
			observations: [
				code,
				{
					...code,
					id: "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
					side: "OLD",
					startLine: 40,
					endLine: 40,
				},
				{
					...code,
					id: "ffffffff-ffff-ffff-ffff-ffffffffffff",
					sourceKind: "scm.review-threads",
					path: "Review thread on ReviewQueryService.java",
					startLine: 3,
					endLine: 3,
					side: undefined,
				},
			],
		},
	},
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table", TABLE);
		within(table).getByText(`${codePath}:40`);
		await expect(within(table).getByText("Before")).toBeVisible();
		within(table).getByText("Review thread on ReviewQueryService.java");
		await expect(
			within(table).queryByText(/Review thread on ReviewQueryService\.java:/u),
		).toBeNull();
	},
};

/** The result's sheet hides its evidence: the row says its summary and place are hidden. */
export const EvidenceHidden: Story = {
	args: {
		state: {
			status: "ready",
			observations: [
				hiddenRow(first, "cccccccc-cccc-cccc-cccc-cccccccccccc"),
				hiddenRow(other, "dddddddd-dddd-dddd-dddd-dddddddddddd"),
			],
		},
	},
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table", TABLE);
		await expect(within(table).getAllByText("Details hidden")).toHaveLength(2);
		// Two hidden rows of different results still name different results.
		const links = within(table).getAllByRole("link", { name: /^Open result Details hidden/u });
		await expect(new Set(links.map((link) => link.getAttribute("aria-label"))).size).toBe(2);
	},
};

/** A problem of a result an admin marked incorrect says so, as the result's own row does. */
export const ResultMarkedIncorrect: Story = {
	args: {
		state: {
			status: "ready",
			observations: [
				{ ...first, invalidatedAt: first.observedAt },
				...reviewObservations.slice(1, 3),
			],
		},
	},
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table", TABLE);
		await expect(within(table).getByText("Marked incorrect")).toBeVisible();
	},
};

/** The head stands and the rows wait in their shape, so nothing jumps when they arrive. */
export const Loading: Story = {
	args: { state: { status: "loading" } },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("table", TABLE)).toHaveAttribute("aria-busy", "true");
	},
};

export const Empty: Story = {
	args: { state: { status: "empty", filtered: false } },
	play: async ({ canvas }) => {
		await expect(
			within(canvas.getByRole("table", TABLE)).getByText("No problems yet"),
		).toBeVisible();
	},
};

export const FilteredToNothing: Story = {
	args: { state: { status: "empty", filtered: true, onClearFilters: clearFilters } },
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Clear all filters" }));
		await expect(clearFilters).toHaveBeenCalled();
	},
};

export const LoadFailed: Story = {
	args: {
		state: {
			status: "error",
			error: { status: 500, detail: "The observation index is unavailable." },
			onRetry: retry,
		},
	},
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("We could not load problems");
		await userEvent.click(canvas.getByRole("button", { name: "Retry" }));
		await expect(retry).toHaveBeenCalled();
	},
};
