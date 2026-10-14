import type { Meta, StoryObj } from "@storybook/react-vite";
import type { ComponentProps } from "react";
import { expect, within } from "storybook/test";

import type { ItemRef, ProfileReview, ResultList } from "@/api/types.gen";
import { useStoryFeedbackResponses } from "@/stories/feedback-ratings";
import {
	detailFeedback,
	detailResult,
	listedResult,
	metWithExample,
	ownFeedback,
	reviewRef,
} from "@/stories/practice-detail-story-mock-data";
import { pullRequest } from "@/stories/practice-profile-story-mock-data";
import { expectNoPageOverflow } from "@/stories/reflow";
import { daysBefore } from "@/stories/story-clock";

import { ReviewCard } from "./ReviewCard";

/** The card with responses a story holds in place of the server. */
function Answerable(props: ComponentProps<typeof ReviewCard>) {
	const responses = useStoryFeedbackResponses();
	return <ReviewCard {...props} responses={responses} />;
}

const pullRequest902: ItemRef = pullRequest(902);
const REVIEW = "00000000-0000-0000-0000-000000000101";

/** A met result of the review below, on a practice and with a summary of its own. */
function met(id: string, practiceSlug: string, practiceName: string, summary: string): ResultList {
	return {
		...listedResult(metWithExample),
		id,
		reviewId: REVIEW,
		practiceSlug,
		practiceName,
		summary,
	};
}

const review: ProfileReview = {
	review: reviewRef(REVIEW, pullRequest902, daysBefore(2)),
	results: [
		met(
			"00000000-0000-0000-0000-000000000102",
			"records-decisions",
			"Record significant decisions",
			"The workspace trade-off is documented",
		),
		{
			...listedResult(detailResult),
			id: "00000000-0000-0000-0000-000000000103",
			reviewId: REVIEW,
			practiceSlug: "keeps-docs-current",
			practiceName: "Keep linked documentation current",
			summary: "A linked page still uses the old component name",
		},
	],
	feedback: [detailFeedback],
};

const meta = {
	component: ReviewCard,
	tags: ["autodocs"],
	parameters: { layout: "padded" },
	args: { review },
	render: (args) => <Answerable {...args} />,
	decorators: [
		(Story) => (
			<ol>
				<Story />
			</ol>
		),
	],
} satisfies Meta<typeof ReviewCard>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * The item at the head, linked; under it every result open, not met first; and the review's
 * feedback once under the results, with its comments and the reader's response.
 */
export const Default: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("link", { name: /^#902/u })).toHaveAttribute(
			"href",
			pullRequest902.url,
		);
		const rows = canvas.getAllByRole("button", { expanded: true });
		await expect(rows[0]).toHaveTextContent("A linked page still uses the old component name");
		await expect(rows[1]).toHaveTextContent("The workspace trade-off is documented");
		const feedback = canvas.getByRole("list", { name: "Your feedback" });
		await expect(within(feedback).getAllByRole("link")).toHaveLength(2);
		await expect(
			within(feedback).getByRole("group", { name: "Your response to the feedback on the work" }),
		).toBeVisible();
	},
};

/** A user view reads the card and answers nothing: the feedback keeps its links, not its controls. */
export const ReadOnly: Story = {
	render: (args) => <ReviewCard {...args} />,
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("list", { name: "Your feedback" })).toBeVisible();
		await expect(canvas.queryByRole("group")).toBeNull();
	},
};

/** A review with no feedback for the reader draws no feedback section at all. */
export const WithoutFeedback: Story = {
	args: { review: { ...review, feedback: [] } },
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("list", { name: "Your feedback" })).toBeNull();
	},
};

/** A Slack thread, named by its channel. */
export const SlackConversation: Story = {
	args: {
		review: {
			...review,
			review: reviewRef(
				"00000000-0000-0000-0000-000000000201",
				{
					...pullRequest902,
					id: "3001",
					kind: "chat.thread",
					provider: "SLACK",
					label: "#backend-guild",
					container: null,
					url: "https://example.slack.com/archives/C01/p1700000000",
				},
				daysBefore(2),
			),
		},
	},
};

/** An Outline document, named by its title. */
export const OutlineDocument: Story = {
	args: {
		review: {
			...review,
			review: reviewRef(
				"00000000-0000-0000-0000-000000000202",
				{
					...pullRequest902,
					id: "4077",
					kind: "docs.document",
					provider: "OUTLINE",
					label: "Runbook: rotating the signing key",
					container: null,
					url: "https://outline.example.com/doc/runbook-rotating-the-signing-key",
				},
				daysBefore(2),
			),
		},
	},
};

/** A GitLab merge request, with GitLab's mark and number. */
export const GitLabMergeRequest: Story = {
	args: {
		review: {
			...review,
			review: reviewRef(
				"00000000-0000-0000-0000-000000000203",
				{
					...pullRequest902,
					id: "1128",
					provider: "GITLAB",
					label: "!128",
					container: "aet/hephaestus",
					url: "https://gitlab.example.com/aet/hephaestus/-/merge_requests/128",
				},
				daysBefore(2),
			),
		},
	},
};

/** An item with no address and feedback with no posted comment: nothing is a link. */
export const WithoutALink: Story = {
	args: {
		review: {
			...review,
			review: reviewRef(
				"00000000-0000-0000-0000-000000000204",
				{ ...pullRequest902, url: null, container: null },
				daysBefore(2),
			),
			feedback: [ownFeedback(detailFeedback.id)],
		},
	},
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("link")).toBeNull();
	},
};

/** A review that reached one practice once, which is every card on a practice's own level. */
const soleReview: ProfileReview = {
	...review,
	review: reviewRef("00000000-0000-0000-0000-000000000206", pullRequest902, daysBefore(2)),
	results: [{ ...listedResult(detailResult), practiceName: "Keep changes focused" }],
};

/**
 * One result is one thing: the summary leads, the item it was seen on is the small line under it,
 * and no head is divided off above them.
 */
export const OneResult: Story = {
	args: { review: soleReview },
	play: async ({ canvas }) => {
		const [, resultRow] = canvas.getAllByRole("listitem");
		if (!resultRow) {
			throw new Error("Expected the card's one result row.");
		}
		// The row is the first thing in the card, so no head is divided off above it.
		const rows = resultRow.closest("ul");
		await expect(rows?.parentElement?.firstElementChild).toBe(rows);

		// The item a head would name is inside the result's own row, still linked.
		const summary = canvas.getByText("The refactor and the fix arrived together");
		const item = canvas.getByRole("link", { name: /^#902/u });
		await expect(resultRow).toContainElement(summary);
		await expect(resultRow).toContainElement(item);
		await expect(canvas.getByText("HephaestusTest/practice-validation")).toBeVisible();
		// The summary is the anchor, so it comes first and the item reads as the note beneath it.
		await expect(summary.compareDocumentPosition(item)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
	},
};

/** Three met results first, as the review lists them, and the one not met result last. */
const denseReview: ProfileReview = {
	...review,
	review: reviewRef("00000000-0000-0000-0000-000000000205", pullRequest902, daysBefore(2)),
	results: [
		met(
			"00000000-0000-0000-0000-000000000111",
			"records-decisions",
			"Record significant decisions",
			"The workspace trade-off is documented",
		),
		met(
			"00000000-0000-0000-0000-000000000105",
			"small-changes",
			"Keep changes focused",
			"The change does one thing",
		),
		met(
			"00000000-0000-0000-0000-000000000109",
			"describe-what-and-why",
			"Describe what changed and why",
			"The description states the goal",
		),
		{
			...listedResult(detailResult),
			id: "00000000-0000-0000-0000-000000000106",
			reviewId: REVIEW,
			practiceSlug: "covers-new-behavior",
			practiceName: "Cover new behavior with a test",
			summary: "The new branch has no test exercising it",
		},
	],
};

export const ManyResults: Story = {
	args: { review: denseReview, initiallyOpen: "none" },
	play: async ({ canvas, userEvent }) => {
		// The not met result is in the first rows, not behind "Show more", although the review lists it last.
		await expect(
			canvas.getByRole("button", { name: /The new branch has no test exercising it/u }),
		).toBeVisible();

		// A met result is the one held back, and the button counts it rather than saying "more".
		const held = "The description states the goal";
		await expect(canvas.queryByText(held)).toBeNull();
		await userEvent.click(canvas.getByRole("button", { name: "Show 1 more result" }));
		await expect(canvas.getByText(held)).toBeVisible();
		for (const result of denseReview.results) {
			await expect(canvas.getByText(result.summary ?? result.practiceName)).toBeVisible();
		}

		await userEvent.click(canvas.getByRole("button", { name: "Show less" }));
		await expect(canvas.queryByText(held)).toBeNull();
		await expect(canvas.getByText("The new branch has no test exercising it")).toBeVisible();
	},
};

export const MobileReflow: Story = {
	args: {
		review: {
			...review,
			review: reviewRef(
				"00000000-0000-0000-0000-000000000207",
				{
					...pullRequest902,
					kind: "docs.document",
					label:
						"Split the practice catalog loader per workspace and move the seeding behind a flag",
					container: "ls1intum/hephaestus-practice-validation-fixtures",
				},
				daysBefore(2),
			),
		},
	},
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320] },
	},
	play: expectNoPageOverflow,
};
