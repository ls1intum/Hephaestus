import type { Meta, StoryObj } from "@storybook/react-vite";
import type { ComponentProps } from "react";
import { expect, fn } from "storybook/test";

import { useStoryFeedbackResponses } from "@/stories/feedback-ratings";
import { detailReviews } from "@/stories/practice-detail-story-mock-data";

import { ItemFeedbackPage } from "./ItemFeedbackPage";
import type { ReviewFeedState } from "./reviews";

/** The page with responses a story holds in place of the server. */
function Answerable(props: ComponentProps<typeof ItemFeedbackPage>) {
	const responses = useStoryFeedbackResponses();
	return <ItemFeedbackPage {...props} responses={responses} />;
}

const readyFeed = {
	status: "ready",
	reviews: detailReviews,
	hasMore: false,
	isLoadingMore: false,
	onLoadMore: fn(),
} satisfies ReviewFeedState;

/**
 * The page a comment on a pull request, merge request or issue links to: the reader's own reviews of
 * that work, where each piece of their feedback can be answered or disagreed with.
 */
const meta = {
	component: ItemFeedbackPage,
	tags: ["autodocs"],
	parameters: { layout: "padded" },
	args: { feed: readyFeed },
	render: (args) => <Answerable {...args} />,
} satisfies Meta<typeof ItemFeedbackPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The reader's reviews of the work, with what a disagreement does said before they write one. */
export const Default: Story = {
	play: async ({ canvas }) => {
		await expect(
			canvas.getByRole("heading", { level: 1, name: "Your feedback on this work" }),
		).toBeVisible();
		await expect(canvas.getByText(/Workspace admins read your reason/u)).toBeVisible();
		await expect(canvas.getByRole("list", { name: "Reviews" })).toBeVisible();
		await expect(
			canvas.getAllByRole("group", { name: /^Your response to the feedback/u }),
		).toHaveLength(2);
	},
};

/** Somebody the comment was not about follows its link: nothing of anybody else's is shown. */
export const NotAboutTheReader: Story = {
	args: { feed: { ...readyFeed, reviews: [] } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Nothing here is about your work")).toBeVisible();
		await expect(canvas.queryByRole("list", { name: "Reviews" })).toBeNull();
	},
};

export const Loading: Story = {
	args: { feed: { status: "loading" } },
	play: async ({ canvas }) => {
		canvas.getByText("Loading reviews…");
		await expect(canvas.queryByRole("list", { name: "Reviews" })).toBeNull();
	},
};

export const LoadFailed: Story = {
	args: {
		feed: {
			status: "error",
			error: { status: 500, detail: "The review history is temporarily unavailable." },
			onRetry: fn(),
		},
	},
	play: async ({ args, canvas, userEvent }) => {
		await expect(canvas.getByText("We could not load reviews")).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: "Retry" }));
		if (args.feed.status !== "error") {
			throw new Error("This story is the error branch");
		}
		await expect(args.feed.onRetry).toHaveBeenCalledOnce();
	},
};

/** A user view reads the page and answers nothing: the feedback keeps its links, not its controls. */
export const ReadOnly: Story = {
	render: (args) => <ItemFeedbackPage {...args} />,
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("list", { name: "Reviews" })).toBeVisible();
		await expect(canvas.queryByRole("group")).toBeNull();
	},
};
