import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, waitFor, within } from "storybook/test";

import type { ReviewSummary } from "@/api/types.gen";
import { ReviewDayTable } from "@/components/practice-trace/ReviewDayTable";
import { expectNoPageOverflow } from "@/stories/reflow";
import { levelsOpenedBy } from "@/test/detail-stack";

import { reviewAccounts, reviews } from "./fixtures";
import { REQUEST_BLOCKED } from "./RequestReviewAction";
import { REVIEW_SUMMARY_COLUMNS, ReviewSummaryRow } from "./ReviewSummaryRow";

function review(id: string): ReviewSummary {
	const found = reviews.find((candidate) => candidate.id === id);
	if (!found) {
		throw new Error(`No review ${id} in the fixture`);
	}
	return found;
}

const completed = review("11111111-1111-1111-1111-111111111111");
const conversation = review("33333333-3333-3333-3333-333333333333");
const running = review("aaaaaaaa-8888-8888-8888-888888888888");
const failed = review("bbbbbbbb-8888-8888-8888-888888888888");
const scan = review("dddddddd-9999-9999-9999-999999999999");
const requested: ReviewSummary = {
	...completed,
	id: "11111111-2222-2222-2222-222222222222",
	origin: "REQUEST",
	startedBy: null,
	requestedBy: reviewAccounts.grace,
};
const requestReview = fn();

/**
 * One row of the admin Reviews list, drawn as the developer's own list draws a review: the time and
 * status, the work, and what started it. The whole row opens the review over the list.
 */
const meta = {
	component: ReviewSummaryRow,
	parameters: { layout: "padded", chromatic: { viewports: [320, 1440] } },
	tags: ["autodocs"],
	args: { review: completed },
	decorators: [
		(Story, { args }) => (
			<ReviewDayTable
				aria-label="Practice reviews, newest first"
				timeLabel="Created"
				columns={REVIEW_SUMMARY_COLUMNS}
				reviews={[args.review]}
				dateOf={(entry) => entry.createdAt}
				reviewKey={(entry) => entry.id}
				renderRow={() => <Story />}
				empty={null}
			/>
		),
	],
} satisfies Meta<typeof ReviewSummaryRow>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * A finished review with nothing notable: no chip beside the work and no count. What it found is on
 * its sheet, and the Feedback tab narrows to the review when it is tracked.
 */
export const Complete: Story = {
	play: async ({ canvas }) => {
		// The status is the icon, named for a screen reader and by its native title, and no button.
		await expect(canvas.getByText("Complete").parentElement).toHaveAttribute("title", "Complete");
		await expect(canvas.queryByRole("button", { name: "Complete" })).not.toBeInTheDocument();
		// The work is text; the review's level links it to the provider.
		await expect(canvas.queryByRole("link", { name: /^#1423/u })).not.toBeInTheDocument();
		canvas.getByText("Event");
		await expect(
			canvas.getByRole("row", { name: /#1423/u }).querySelector("tbody [data-slot=badge]"),
		).toBeNull();
		await expect(
			levelsOpenedBy(canvas.getByRole("link", { name: /^Open review of #1423,/u })),
		).toEqual([`review:${completed.id}`]);
	},
};

/** A review someone asked for names who asked. */
export const Requested: Story = {
	args: { review: requested },
	play: async ({ canvas }) => {
		canvas.getByText("Grace Hopper");
	},
};

/**
 * Request review, the third action after Track and Trace: it asks before it starts a review and
 * names the work it would review.
 */
export const RequestAnotherReview: Story = {
	args: { request: { work: "pull request #1423", onRequest: requestReview } },
	play: async ({ canvas, userEvent }) => {
		requestReview.mockClear();
		await userEvent.click(canvas.getByRole("button", { name: /^Request review of /u }));
		const dialog = await screen.findByRole("alertdialog", {
			name: "Request a review of pull request #1423?",
		});
		await userEvent.click(within(dialog).getByRole("button", { name: "Request review" }));
		await expect(requestReview).toHaveBeenCalledOnce();
		await waitFor(async () => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
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
	},
};

/** The reviewed work is not always a pull request, and the row's icon says which kind it was. */
export const AConversation: Story = {
	args: { review: conversation },
	play: async ({ canvas }) => {
		canvas.getByRole("link", { name: /^Open review of #engineering,/u });
	},
};

/** Nothing decided yet: the status icon says it is running, and no chip stands beside the work. */
export const StillRunning: Story = {
	args: { review: running },
	play: async ({ canvas }) => {
		canvas.getByText("Running");
	},
};

/** A review that failed is marked beside the work, as well as by its status icon. */
export const Failed: Story = {
	args: { review: failed },
	play: async ({ canvas }) => {
		await expect(canvas.getAllByText("Failed")).toHaveLength(2);
	},
};

/** A scan that closed with files it did not review is marked Partial beside the repository. */
export const PartialScan: Story = {
	args: { review: scan },
	play: async ({ canvas }) => {
		await expect(canvas.getAllByText("Partial")).toHaveLength(2);
	},
};

/** At the reflow width the table scrolls inside its frame, never the page. */
export const Narrow: Story = {
	parameters: { viewport: { defaultViewport: "reflow" } },
	play: async () => {
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
