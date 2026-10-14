import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";

import { InlineLink } from "@/components/common/InlineLink";
import { groups } from "@/stories/practice-profile-story-mock-data";
import { daysBefore } from "@/stories/story-clock";

import { notMetOwnResult as notMet, ownItemTrace, ownResults, reusedOwnResult } from "./fixtures";
import { ReviewPracticeTable, ReviewPracticeTableSkeleton } from "./ReviewPracticeTable";

const GROUP_OF_PRACTICE: Record<string, string> = {
	"thin-controllers": "review-ready-work",
	"descriptive-pull-requests": "review-ready-work",
	"product-language": "communication",
};

const groupOf = (practiceSlug: string) =>
	groups.find((group) => group.slug === GROUP_OF_PRACTICE[practiceSlug]);

/**
 * The reader's own results in one review: each result's outcome, its practice under its group, and
 * its own words. A result reused from an earlier review links to that review.
 */
const meta = {
	component: ReviewPracticeTable,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		results: ownResults,
		reviewId: ownItemTrace.review.id,
		groupOf,
		filters: {},
		onFiltersChange: fn(),
		onOpenPractice: fn(),
		earlierReviewLink: () => <InlineLink href="#earlier">Open the earlier review</InlineLink>,
		emptyMessage: "This review recorded no result about you.",
	},
	argTypes: {
		results: { control: false },
		groupOf: { control: false },
		earlierReviewLink: { control: false },
	},
} satisfies Meta<typeof ReviewPracticeTable>;

export default meta;
type Story = StoryObj<typeof meta>;

/** One line per result: its words, then its verdict, which counts its problems and never ranks them. */
export const Default: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Two controllers query the database directly")).toBeVisible();
		await expect(canvas.getByText("Not met · 2 problems")).toBeVisible();
		await expect(canvas.queryByText("Major")).not.toBeInTheDocument();
	},
};

/** A result the review reused keeps the id of the review that recorded it, and links there. */
export const ReusedFromAnEarlierReview: Story = {
	args: { results: [reusedOwnResult] },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("link", { name: "Open the earlier review" })).toBeVisible();
	},
};

export const MarkedIncorrect: Story = {
	args: { results: [{ ...notMet, markedIncorrectAt: daysBefore(1) }] },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Marked incorrect")).toBeVisible();
	},
};

/** The practice the reader came to see opens on arrival, with its evidence in place. */
export const FocusedPractice: Story = {
	args: {
		focusPracticeSlug: notMet.practiceSlug,
		renderResult: (resultId) => <p>Evidence for {resultId}</p>,
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText(`Evidence for ${notMet.id}`)).toBeVisible();
		await expect(
			canvas.getByRole("button", { name: `Hide evidence for ${notMet.practiceName}` }),
		).toHaveAttribute("aria-expanded", "true");
	},
};

export const Filtered: Story = {
	args: { filters: { practice: "thin" } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Thin controllers")).toBeVisible();
		await expect(canvas.queryByText("Product language")).not.toBeInTheDocument();
	},
};

export const NarrowViewport: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
};

/** No result about the reader, which only the caller can explain. */
export const Empty: Story = {
	args: { results: [] },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("This review recorded no result about you.")).toBeVisible();
	},
};

/** The table's own shape while its rows load, so nothing jumps when they land. */
export const Loading: Story = {
	render: () => <ReviewPracticeTableSkeleton rows={3} />,
	play: async ({ canvas }) => {
		await expect(
			canvas.getByRole("table", { name: "Every practice in this review" }),
		).toHaveAttribute("aria-busy", "true");
	},
};
