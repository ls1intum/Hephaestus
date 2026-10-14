import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, waitFor, within } from "storybook/test";

import type { ReviewRunSummary } from "@/api/types.gen";
import { ReviewRunDayTable } from "@/components/practice-trace/ReviewRunDayTable";
import { expectNoPageOverflow } from "@/stories/reflow";
import { levelsOpenedBy } from "@/test/detail-stack";

import { reviewRuns } from "./fixtures";
import { REQUEST_BLOCKED } from "./RequestReviewAction";
import { REVIEW_RUN_COLUMNS, ReviewRunRow } from "./ReviewRunRow";

function run(id: string): ReviewRunSummary {
	const found = reviewRuns.find((review) => review.id === id);
	if (!found) {
		throw new Error(`No review ${id} in the fixture`);
	}
	return found;
}

const completed = run("11111111-1111-1111-1111-111111111111");
const conversation = run("33333333-3333-3333-3333-333333333333");
const running = run("aaaaaaaa-8888-8888-8888-888888888888");
const failed = run("bbbbbbbb-8888-8888-8888-888888888888");
const processingFailed: ReviewRunSummary = { ...completed, resultProcessing: "FAILED" };
const requestedAgain: ReviewRunSummary = {
	...completed,
	id: "11111111-2222-2222-2222-222222222222",
	origin: "MANUAL",
	positionOnWork: 2,
	reviewsOnWork: 3,
};
const onHold: ReviewRunSummary = { ...running, status: "QUEUED", holdReason: "BUDGET" };
const finishedAfterMerge: ReviewRunSummary = { ...completed, finishedAfterMerge: true };
const startedByMerge: ReviewRunSummary = {
	...finishedAfterMerge,
	finishedAfterMergeStartedByMerge: true,
};
const requestReview = fn();

/**
 * One row of the admin Reviews list, drawn as the developer's own list draws a review: the time and
 * status, the work, who it was about, and its result. The whole row opens the review over the list.
 */
const meta = {
	component: ReviewRunRow,
	parameters: { layout: "padded", chromatic: { viewports: [320, 1440] } },
	tags: ["autodocs"],
	args: { review: completed },
	decorators: [
		(Story, { args }) => (
			<ReviewRunDayTable
				aria-label="Practice reviews, newest first"
				timeLabel="Created"
				columns={REVIEW_RUN_COLUMNS}
				runs={[args.review]}
				dateOf={(review) => review.createdAt}
				runKey={(review) => review.id}
				renderRow={() => <Story />}
				empty={null}
			/>
		),
	],
} satisfies Meta<typeof ReviewRunRow>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * A finished review with nothing notable: no chip beside the work and no count. What it found, the
 * verdicts, the feedback and how it was occasioned are on its sheet, and the Feedback tab narrows to
 * the review when it is tracked.
 */
export const Completed: Story = {
	play: async ({ canvas }) => {
		// The status is the icon, named for a screen reader and by its native title, and no button:
		// the row keeps its link and its Track and Trace.
		await expect(canvas.getByText("Completed").parentElement).toHaveAttribute("title", "Completed");
		await expect(canvas.queryByRole("button", { name: "Completed" })).not.toBeInTheDocument();
		// The work is text; the review's level links it to the provider.
		await expect(canvas.queryByRole("link", { name: /^#1423/u })).not.toBeInTheDocument();
		canvas.getByText("Ada Lovelace");
		// No result column: neither the count nor the practices by name.
		await expect(canvas.queryByText(/not met$/u)).not.toBeInTheDocument();
		await expect(canvas.queryByText(/Errors carry their context/u)).not.toBeInTheDocument();
		await expect(canvas.queryByRole("columnheader", { name: "Result" })).not.toBeInTheDocument();
		await expect(
			canvas.getByRole("row", { name: /#1423/u }).querySelector("[data-slot=badge]"),
		).toBeNull();
		await expect(canvas.queryByText(/Feedback:/u)).not.toBeInTheDocument();
		await expect(canvas.queryByText("Live")).not.toBeInTheDocument();
		await expect(
			levelsOpenedBy(canvas.getByRole("link", { name: /^Open review of #1423,/u })),
		).toEqual([`review:${completed.id}`]);
	},
};

/**
 * A later review of the same work says which it is. That someone requested it is on its sheet,
 * not in the row.
 */
export const RequestedAgain: Story = {
	args: { review: requestedAgain },
	play: async ({ canvas }) => {
		canvas.getByText("Review 2 of 3");
		canvas.getByText("Created");
		await expect(canvas.queryByText("Requested")).not.toBeInTheDocument();
	},
};

/**
 * Request review, the third action after Track and Trace: it asks before it starts a review, since
 * that runs an agent job, and names the work it would review.
 */
export const RequestAnotherReview: Story = {
	args: { request: { work: "pull request #1423", onRequest: requestReview } },
	play: async ({ canvas, userEvent }) => {
		requestReview.mockClear();
		const actions = canvas
			.getAllByRole("button")
			.map((button) => button.getAttribute("aria-label"));
		await expect(actions).toEqual([
			"About the row actions",
			expect.stringMatching(/^Track /u),
			"Request review of pull request #1423",
		]);
		await userEvent.click(canvas.getByRole("button", { name: /^Request review of /u }));
		const dialog = await screen.findByRole("alertdialog", {
			name: "Request a review of pull request #1423?",
		});
		await userEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));
		await expect(requestReview).not.toHaveBeenCalled();
		// The keyboard reaches it and opens it as a pointer does.
		canvas.getByRole("button", { name: /^Request review of /u }).focus();
		await userEvent.keyboard("{Enter}");
		const again = await screen.findByRole("alertdialog");
		await userEvent.click(within(again).getByRole("button", { name: "Request review" }));
		await expect(requestReview).toHaveBeenCalledOnce();
		// Confirming closes the dialog: the toast and the list say what came of it.
		await waitFor(async () => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
	},
};

/**
 * The row's controls stand in an actions column of their own, each the app's 32 px icon button. A
 * press in that column, between the buttons too, is never a press on the row.
 */
export const ActionsStandApart: Story = {
	args: { request: { work: "pull request #1423", onRequest: requestReview } },
	play: async ({ canvas, canvasElement, userEvent }) => {
		canvas.getByRole("columnheader", { name: /Actions/u });
		const cell = canvasElement.querySelector<HTMLElement>("[data-row-actions]");
		if (cell === null) {
			throw new Error("The row has no actions column");
		}
		const buttons = within(cell).getAllByRole("button");
		await expect(buttons.map((button) => button.getAttribute("aria-label"))).toEqual([
			expect.stringMatching(/^Track /u),
			"Request review of pull request #1423",
		]);
		within(cell).getByRole("link", { name: /^Trace /u });
		for (const control of [...buttons, within(cell).getByRole("link")]) {
			const box = control.getBoundingClientRect();
			await expect(box.width).toBeGreaterThanOrEqual(32);
			await expect(box.height).toBeGreaterThanOrEqual(32);
		}
		// The link to open the row is not in the actions column.
		await expect(cell.querySelector("[data-row-link]")).toBeNull();
		const opened = fn();
		canvasElement.querySelector("[data-row-link]")?.addEventListener("click", (event) => {
			event.preventDefault();
			opened();
		});
		await userEvent.click(cell);
		await expect(opened).not.toHaveBeenCalled();
		await userEvent.click(canvas.getByText("Ada Lovelace"));
		await expect(opened).toHaveBeenCalledOnce();
		// The head's (i) says what each control does, one line each.
		await userEvent.click(canvas.getByRole("button", { name: "About the row actions" }));
		const about = await screen.findByRole("dialog", { name: "About the row actions" });
		for (const term of ["Track", "Trace", "Request review"]) {
			within(about).getByText(term);
		}
		await userEvent.keyboard("{Escape}");
	},
};

/**
 * The confirmation is a real modal. While it is open the table behind is inert, so no press reaches
 * a row and no Tab leaves the dialog; a press inside it is not a press on the row it came from;
 * Escape closes it and focus returns to the button.
 */
export const RequestDialogIsModal: Story = {
	args: { request: { work: "pull request #1423", onRequest: requestReview } },
	play: async ({ canvas, canvasElement, userEvent }) => {
		requestReview.mockClear();
		const rowLink = canvasElement.querySelector<HTMLElement>("[data-row-link]");
		if (rowLink === null) {
			throw new Error("The row has no link");
		}
		const opened = fn();
		rowLink.addEventListener("click", (event) => {
			event.preventDefault();
			opened();
		});
		const button = canvas.getByRole("button", { name: /^Request review of /u });
		await userEvent.click(button);
		const dialog = await screen.findByRole("alertdialog");
		// Everything behind the dialog is inert: the table's own branch of the page included.
		const background = [...document.body.children].find((child) => child.contains(canvasElement));
		await expect(background).toHaveProperty("inert", true);
		await expect(dialog.closest("[inert]")).toBeNull();
		// A press on the dialog's text bubbles through React to the row, which must not open.
		await userEvent.click(within(dialog).getByText(/counts toward this/u));
		await expect(opened).not.toHaveBeenCalled();
		screen.getByRole("alertdialog");
		await userEvent.keyboard("{Escape}");
		await waitFor(async () => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
		await expect(background).toHaveProperty("inert", false);
		await waitFor(async () => expect(button).toHaveFocus());
		await expect(requestReview).not.toHaveBeenCalled();
	},
};

/** While a review of the work is queued or running, another would only join it: disabled, with why. */
export const RequestBlockedWhileRunning: Story = {
	args: {
		review: running,
		request: { work: "pull request #1436", blocked: REQUEST_BLOCKED.active },
	},
	play: async ({ canvas, userEvent }) => {
		const button = canvas.getByRole("button", { name: /^Request review of /u });
		await expect(button).toHaveAttribute("aria-disabled", "true");
		await userEvent.hover(button);
		await waitFor(async () => expect(screen.getByText(REQUEST_BLOCKED.active)).toBeVisible());
		await userEvent.click(button);
		await expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
	},
};

/**
 * A review a newer one replaced: the status says Replaced, and a chip beside the work says it again
 * in the words the list scans for.
 */
export const ReplacedByANewerReview: Story = {
	args: { review: { ...completed, reviewOutcome: "SUPERSEDED" } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Replaced").parentElement).toHaveAttribute("title", "Replaced");
		canvas.getByText("Replaced by newer");
		await expect(canvas.queryByText(/not met$/u)).not.toBeInTheDocument();
	},
};

/** The reviewed work is not always a pull request, and the row's icon says which kind it was. */
export const AConversation: Story = {
	args: { review: conversation },
	play: async ({ canvas }) => {
		canvas.getByRole("link", { name: /^Open review of #engineering,/u });
	},
};

/**
 * A review can run to the end and still fail to process what it produced. The status says it
 * completed, so the failure is the chip beside the work.
 */
export const ResultProcessingFailed: Story = {
	args: { review: processingFailed },
	play: async ({ canvas }) => {
		canvas.getByText("Completed");
		canvas.getByText("Result processing failed");
	},
};

/**
 * Nothing decided yet: the status icon says it is running, and the result stays empty rather than
 * reading as a review that found nothing.
 */
export const StillRunning: Story = {
	args: { review: running },
	play: async ({ canvas }) => {
		canvas.getByText("Running");
		await expect(canvas.queryByText("Nothing to improve")).not.toBeInTheDocument();
	},
};

/** A review that failed is marked beside the work, as well as by its status icon. */
export const StoppedWithNothing: Story = {
	args: { review: failed },
	play: async ({ canvas }) => {
		await expect(canvas.getAllByText("Failed")).toHaveLength(2);
		await expect(canvas.queryByText("Nothing to improve")).not.toBeInTheDocument();
		await expect(canvas.queryByText(/not met$/u)).not.toBeInTheDocument();
	},
};

/** At the reflow width the table scrolls inside its frame, never the page. */
export const Narrow: Story = {
	parameters: { viewport: { defaultViewport: "reflow" } },
	play: async ({ canvas }) => {
		canvas.getByText("Ada Lovelace");
		await expectNoPageOverflow();
	},
};

/**
 * The review open in the panel beside the list keeps a bar on its row's leading edge, so the list
 * says which row the panel belongs to.
 */
export const OpenInThePanel: Story = {
	args: { open: true },
	play: async ({ canvas }) => {
		const row = canvas.getByRole("row", { name: /#1423/u });
		await expect(row).toHaveAttribute("data-state", "open");
	},
};

/** A review that ran past its time is marked Timed out beside the work. */
export const TimedOut: Story = {
	args: { review: { ...failed, status: "TIMED_OUT" } },
	play: async ({ canvas }) => {
		await expect(canvas.getAllByText("Timed out")).toHaveLength(2);
	},
};

/** Several notable states at once each get their chip, in a fixed order. */
export const SeveralMarkers: Story = {
	args: {
		review: {
			...completed,
			reviewOutcome: "SUPERSEDED",
			resultProcessing: "FAILED",
			finishedAfterMerge: true,
		},
	},
	play: async ({ canvasElement }) => {
		const chips = [...canvasElement.querySelectorAll("tbody [data-slot=badge]")].map(
			(chip) => chip.textContent,
		);
		await expect(chips).toEqual([
			"Replaced by newer",
			"Result processing failed",
			"Finished after the merge",
		]);
	},
};

/** A review held for the AI budget is marked on hold, and says which hold on hover. */
export const OnHold: Story = {
	args: { review: onHold },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("On hold").parentElement).toHaveAttribute(
			"title",
			"AI budget pause",
		);
	},
};

/** A review that finished after its work was merged came too late, and is marked so. */
export const FinishedAfterMerge: Story = {
	args: { review: finishedAfterMerge },
	play: async ({ canvas }) => {
		canvas.getByText("Finished after the merge");
	},
};

/** A review the merge itself started could only finish after it: no marker. */
export const StartedByTheMerge: Story = {
	args: { review: startedByMerge },
	play: async ({ canvas }) => {
		await expect(canvas.queryByText("Finished after the merge")).not.toBeInTheDocument();
	},
};
