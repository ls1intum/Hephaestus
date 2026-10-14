import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";

import type { PracticeScriptRun, ReviewPractice } from "@/api/types.gen";
import { currentDefinition } from "@/components/admin/practices/practice-definition-fields";
import { expectNoPageOverflow, horizontalScrollParentOf } from "@/stories/reflow";
import { levelsOpenedBy } from "@/test/detail-stack";

import { reviewPracticeScripts, workspacePractices } from "./fixtures";
import { ReviewPracticeScriptTable } from "./ReviewPracticeScriptTable";

const PRACTICE_STATUS_OF_RUN = {
	OK: "DONE",
	WITHHELD: "WITHHELD",
	FAILED: "FAILED",
	NOT_REACHED: "FAILED",
	NOT_FINISHED: "FAILED",
} satisfies Record<PracticeScriptRun["status"], ReviewPractice["status"]>;

/** The review's entry for the practice whose program made this run. */
function scripted(run: PracticeScriptRun): ReviewPractice {
	const practice = workspacePractices.find((entry) => entry.id === run.practiceId);
	if (!practice) {
		throw new Error(`No practice ${run.practiceId} in the fixture catalog`);
	}
	const returned = run.status === "OK";
	const withheld = run.status === "WITHHELD";
	return {
		practiceSlug: practice.slug,
		practiceName: currentDefinition(practice).name,
		practiceVersionId: run.practiceVersionId,
		number: practice.currentVersion.number,
		status: PRACTICE_STATUS_OF_RUN[run.status],
		reason: null,
		itemCounts: {
			listed: 1,
			reviewed: returned ? 1 : 0,
			reused: 0,
			withheld: withheld ? 1 : 0,
			notReviewed: returned || withheld ? 0 : 1,
		},
		results: { met: returned ? 1 : 0, notMet: 0, doesNotApply: 0, couldNotDetermine: 0 },
		script: run,
	};
}

/** The row of one practice's script, found by the practice that heads it. */
function scriptRow(table: HTMLElement, practice: string) {
	const row = within(table).getByRole("rowheader", { name: practice }).closest("tr");
	if (row === null) {
		throw new Error(`No row for ${practice}`);
	}
	return within(row);
}

/** The cell under a column header, in one script's row. */
function cellUnder(table: HTMLElement, row: ReturnType<typeof scriptRow>, column: string) {
	const index = within(table)
		.getAllByRole("columnheader")
		.findIndex((head) => head.textContent === column);
	const cell = [...row.getAllByRole("rowheader"), ...row.getAllByRole("cell")][index];
	if (cell === undefined) {
		throw new Error(`No cell under ${column}`);
	}
	return cell;
}

/** The section of the operations guide that a guide link opens. */
const guideSection = (link: HTMLElement) => new URL(link.getAttribute("href") ?? "").hash;

/**
 * How each practice's program ran in one review: its own script, or the built-in review a
 * practice without one gets. The result is words, never a badge: it is about the program, not about
 * the developer's work. A practice whose program did not run in the review has no row.
 *
 * No cost and no runner token counts: spend is on AI usage, and the review's own model calls are on
 * the run card.
 */
const meta = {
	component: ReviewPracticeScriptTable,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		practices: [
			reviewPracticeScripts.fromCode,
			reviewPracticeScripts.withModels,
			reviewPracticeScripts.builtIn,
			reviewPracticeScripts.withheld,
			reviewPracticeScripts.failed,
			reviewPracticeScripts.outOfTime,
			reviewPracticeScripts.notFinished,
		].map(scripted),
	},
	argTypes: { practices: { control: false } },
} satisfies Meta<typeof ReviewPracticeScriptTable>;

export default meta;
type Story = StoryObj<typeof meta>;

export const EveryResult: Story = {
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table");
		await expect(
			within(table)
				.getAllByRole("columnheader")
				.map((head) => head.textContent),
		).toEqual(["Practice", "Result", "Models", "Calls", "Time"]);
		// The counts line up on the right, under their headers.
		for (const column of ["Calls", "Time"]) {
			const head = within(table).getByRole("columnheader", { name: column });
			await expect(getComputedStyle(head).textAlign).toBe("right");
		}
		// How each program reached its result, or why it has none, in the order the rows came.
		const rows = within(table).getAllByRole("row").slice(1);
		await expect(
			// The result's own words lead its cell, ahead of any fix or error under them.
			rows.map(
				(row) => cellUnder(table, within(row), "Result").firstChild?.firstChild?.textContent,
			),
		).toEqual([
			"Returned a result",
			"Returned a result",
			"Returned a result",
			"Withheld",
			"Failed: error",
			"Failed: ran out of time",
			"Did not finish",
		]);
		await expect(canvas.queryByText(/\$|USD/u)).not.toBeInTheDocument();
	},
};

export const ResultFromCode: Story = {
	args: { practices: [scripted(reviewPracticeScripts.fromCode)] },
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table");
		const row = scriptRow(table, "Thin controllers");
		await expect(row.getByText("Returned a result")).toBeVisible();
		// The kind's name is visible, the tier's for a screen reader, beside its icon.
		await expect(row.getByText("Decision model")).toBeVisible();
		await expect(row.getByText("Cloud")).toHaveClass("sr-only");
		await expect(cellUnder(table, row, "Calls")).toHaveTextContent(/^12$/u);
		await expect(cellUnder(table, row, "Time")).toHaveTextContent(/^4\.2 s$/u);
		// The practice names its row for a screen reader.
		await expect(row.getByRole("rowheader", { name: "Thin controllers" })).toHaveAttribute(
			"scope",
			"row",
		);
		await expect(levelsOpenedBy(row.getByRole("link", { name: "Thin controllers" }))).toStrictEqual(
			["practice:thin-controllers"],
		);
	},
};

/** A run of a minute or more reads in minutes, not in thousands of seconds. */
export const LongRun: Story = {
	args: { practices: [scripted({ ...reviewPracticeScripts.fromCode, durationMs: 3_725_000 })] },
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table");
		const row = scriptRow(table, "Thin controllers");
		await expect(cellUnder(table, row, "Time")).toHaveTextContent(/^62 min 5 s$/u);
	},
};

/** Each model the script called, with the tier of the model it reached. Calls add up across them. */
export const WithModels: Story = {
	args: { practices: [scripted(reviewPracticeScripts.withModels)] },
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table");
		const row = scriptRow(table, "Errors carry their context");
		await expect(row.getByText("Decision model")).toBeVisible();
		await expect(row.getByText("Embedding model")).toBeVisible();
		await expect(cellUnder(table, row, "Calls")).toHaveTextContent(/^11$/u);
		await expect(cellUnder(table, row, "Time")).toHaveTextContent(/^12\.8 s$/u);
	},
};

/** The practice was withheld, so its program did not run, asked nothing and took no time. */
export const Withheld: Story = {
	args: { practices: [scripted(reviewPracticeScripts.withheld)] },
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table");
		const row = scriptRow(table, "Product language");
		await expect(row.getByText("Withheld")).toBeVisible();
		await expect(cellUnder(table, row, "Calls")).toHaveTextContent("—none");
		await expect(cellUnder(table, row, "Time")).toHaveTextContent("—none");
		await expect(
			row.queryByRole("link", { name: /for Product language/u }),
		).not.toBeInTheDocument();
	},
};

/**
 * The first line of the script's error, so its author can fix it without a log. It sits under the
 * result and wraps there, so a narrow screen shows all of it.
 */
export const FailedWithError: Story = {
	args: { practices: [scripted(reviewPracticeScripts.failed)] },
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table");
		const row = scriptRow(table, "Tests name the behaviour");
		await expect(row.getByText("Failed: error")).toBeVisible();
		// A script that stopped at once still ran, so it does not read as 0.0 s.
		await expect(cellUnder(table, row, "Time")).toHaveTextContent(/^< 0\.1 s$/u);
		const error = row.getByText(/^TypeError: change\.files is not iterable/u);
		await expect(error).toBeVisible();
		await expect(error.closest("td")).toBe(cellUnder(table, row, "Result"));
		const edit = row.getByRole("link", { name: "Edit the script for Tests name the behaviour" });
		await expect(edit.getAttribute("href")).toMatch(/^\/w\/demo\/admin\/practices\?/u);
		await expect(levelsOpenedBy(edit)).toStrictEqual(["practice-edit:tests-name-the-behaviour"]);
	},
};

/**
 * The stage stopped before the script reported, so the run has no time to show. Nothing on this
 * page fixes that, so the result links the guide that says what to raise or speed up.
 */
export const NotFinished: Story = {
	args: { practices: [scripted(reviewPracticeScripts.notFinished)] },
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table");
		const row = scriptRow(table, "Decisions are written down");
		await expect(row.getByText("Did not finish")).toBeVisible();
		await expect(cellUnder(table, row, "Time")).toHaveTextContent("—none");
		const guide = row.getByRole("link", {
			name: /^Why scripts stop early for Decisions are written down/u,
		});
		await expect(guideSection(guide)).toBe("#the-trace-line");
	},
};

/**
 * The review ended before this program's turn came: it asked nothing and took no time. Nothing on
 * this page brings the turn back, so the guide that says why programs stop early is the way out.
 */
export const NotReached: Story = {
	args: { practices: [scripted(reviewPracticeScripts.notReached)] },
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table");
		const row = scriptRow(table, "Decisions are written down");
		await expect(row.getByText("Not reached")).toBeVisible();
		await expect(cellUnder(table, row, "Calls")).toHaveTextContent("—none");
		await expect(cellUnder(table, row, "Time")).toHaveTextContent("—none");
		const guide = row.getByRole("link", {
			name: /^Why scripts stop early for Decisions are written down/u,
		});
		await expect(guideSection(guide)).toBe("#the-trace-line");
	},
};

/**
 * A practice without a script of its own is reviewed by the built-in review. It declares no models
 * and has no script anyone here can edit, so its row offers no fix.
 */
export const BuiltInReview: Story = {
	args: { practices: [scripted(reviewPracticeScripts.builtIn)] },
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table");
		const row = scriptRow(table, "The change explains itself");
		await expect(row.getByText("Returned a result")).toBeVisible();
		await expect(cellUnder(table, row, "Models")).toHaveTextContent("—none");
		await expect(row.queryByRole("link", { name: /Edit the script/u })).not.toBeInTheDocument();
	},
};

/**
 * The table scrolls inside its own frame, and the page does not. The practice and its result fit
 * the screen, so a failed script's error wraps inside the result's column and shows all of it
 * before any scrolling. The figures to the right scroll into view.
 */
export const NarrowViewport: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table");
		await expect(table).toBeVisible();
		await expectNoPageOverflow();
		const error = within(table).getByText(/^TypeError:/u);
		const cell = error.closest("td");
		await expect(cell).not.toBeNull();
		await expect(error.getBoundingClientRect().right).toBeLessThanOrEqual(
			cell?.getBoundingClientRect().right ?? 0,
		);
		// The whole result column is inside the frame before it scrolls: nothing of the error is cut.
		const frame = horizontalScrollParentOf(table);
		await expect(frame.scrollLeft).toBe(0);
		await expect(cell?.getBoundingClientRect().right ?? Infinity).toBeLessThanOrEqual(
			frame.getBoundingClientRect().right,
		);
		// More than one line: it wraps rather than running out of its column.
		await expect(error.getBoundingClientRect().height).toBeGreaterThan(
			Number.parseFloat(getComputedStyle(error).lineHeight) * 1.5,
		);
	},
};
