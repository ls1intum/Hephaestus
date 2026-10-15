import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";

import type { PracticeItemTypeOption, ReviewOn } from "@/api/types.gen";
import { expectNoOverflowingElement } from "@/stories/reflow";
import { Stateful } from "@/stories/stateful";

import { expectUnavailable } from "@/test/controls";
import {
	definitionOf,
	mockDocumentItemType,
	mockFileItemType,
	mockIssueItemType,
	mockPullRequestItemType,
	mockPullRequestReviewItemType,
	mockThreadItemType,
} from "./fixtures";

import { ReviewOnEditor } from "./ReviewOnEditor";

const meta = {
	component: ReviewOnEditor,
	args: {
		itemType: mockPullRequestItemType,
		reviewOn: definitionOf().reviewOn,
		onChange: fn(),
	},
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	render: (args) => (
		<Stateful initial={args.reviewOn}>
			{(reviewOn, setReviewOn) => (
				<ReviewOnEditor
					{...args}
					reviewOn={reviewOn}
					onChange={(next) => {
						args.onChange(next);
						setReviewOn(next);
					}}
				/>
			)}
		</Stateful>
	),
} satisfies Meta<typeof ReviewOnEditor>;

export default meta;
type Story = StoryObj<typeof meta>;

export const PullRequest: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Starts")).toBeVisible();
		await expect(canvas.getByText("Along the way")).toBeVisible();
		await expect(canvas.getByText("Ends")).toBeVisible();
		await expect(canvas.getByRole("group", { name: "When" })).toBeVisible();
	},
};

export const PullRequestReview: Story = {
	args: {
		itemType: mockPullRequestReviewItemType,
		reviewOn: { events: ["scm.pull_request_review.submitted"], state: {} },
	},
	play: async ({ canvas }) => {
		const strip = within(canvas.getByRole("group", { name: "Review on" }));
		await expect(
			strip.getByRole("checkbox", { name: "Pull request review submitted" }),
		).toBeChecked();
		await expect(strip.getAllByRole("checkbox")).toHaveLength(1);
		await expect(canvas.queryByRole("group", { name: "When" })).toBeNull();
	},
};

export const Conversation: Story = {
	args: { itemType: mockThreadItemType, reviewOn: { events: ["chat.thread.settled"], state: {} } },
	play: async ({ canvas }) => {
		await expect(canvas.queryByText("Ends")).toBeNull();
	},
};

export const NoEventChosen: Story = {
	args: { reviewOn: { events: [], state: {} } },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(
				"With no event chosen, this practice is reviewed only when someone requests a review.",
			),
		).toBeVisible();
	},
};

export const AnItemWithoutEvents: Story = {
	args: { itemType: mockFileItemType, reviewOn: { events: [], state: {} } },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(
				"This kind of work has no events. It is reviewed when someone requests a review.",
			),
		).toBeVisible();
	},
};

const ALL_ITEM_TYPES: { itemType: PracticeItemTypeOption; reviewOn: ReviewOn }[] = [
	{ itemType: mockPullRequestItemType, reviewOn: definitionOf().reviewOn },
	{ itemType: mockIssueItemType, reviewOn: { events: ["scm.issue.opened"], state: {} } },
	{ itemType: mockDocumentItemType, reviewOn: { events: ["docs.document.published"], state: {} } },
	{ itemType: mockThreadItemType, reviewOn: { events: ["chat.thread.settled"], state: {} } },
];

export const EveryItemType: Story = {
	render: (args) => (
		<div className="space-y-8">
			{ALL_ITEM_TYPES.map(({ itemType, reviewOn }) => (
				<section key={itemType.kind} className="space-y-2">
					<h3 className="text-sm font-semibold">{itemType.label}</h3>
					<ReviewOnEditor {...args} itemType={itemType} reviewOn={reviewOn} />
				</section>
			))}
		</div>
	),
	play: async ({ canvas }) => {
		await expect(canvas.getAllByRole("group", { name: "Review on" })).toHaveLength(4);
	},
};

// Retain unsupported selected events so the author can remove them before saving.
export const AnEventTheItemTypeNoLongerOffers: Story = {
	args: {
		reviewOn: { events: ["scm.pull_request.opened", "scm.pull_request.reviewed"], state: {} },
	},
	play: async ({ args, canvas, userEvent }) => {
		const stray = canvas.getByRole("checkbox", { name: "An event this version no longer offers" });
		await expect(stray).toBeChecked();

		await userEvent.click(stray);
		await expect(args.onChange).toHaveBeenCalledWith({
			events: ["scm.pull_request.opened"],
			state: {},
		});
		await expect(
			canvas.queryByRole("checkbox", { name: "An event this version no longer offers" }),
		).toBeNull();
	},
};

export const Invalid: Story = {
	args: {
		reviewOn: { events: ["scm.pull_request.reviewed"], state: {} },
		eventsErrorId: "practice-review-settings-error",
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("group", { name: "Review on" })).toHaveAttribute(
			"data-invalid",
			"true",
		);
	},
};

export const Disabled: Story = {
	args: { disabled: true },
	play: async ({ canvas }) => {
		await expectUnavailable(canvas.getByRole("checkbox", { name: /^Opened/u }));
	},
};

export const TogglingEventsAndStates: Story = {
	play: async ({ args, canvas, userEvent }) => {
		const strip = within(canvas.getByRole("group", { name: "Review on" }));
		await expect(strip.getByRole("checkbox", { name: /^Merged/u })).not.toBeChecked();

		await userEvent.click(strip.getByText("Merged"));
		await expect(strip.getByRole("checkbox", { name: /^Merged/u })).toBeChecked();
		await expect(args.onChange).toHaveBeenLastCalledWith({
			events: ["scm.pull_request.merged", "scm.pull_request.opened", "scm.pull_request.ready"],
			state: { draftStatus: ["NOT_DRAFT"] },
		});

		await userEvent.click(canvas.getByRole("checkbox", { name: "Draft" }));
		await expect(canvas.getByRole("checkbox", { name: "Draft" })).toBeChecked();
		await expect(args.onChange).toHaveBeenLastCalledWith(expect.objectContaining({ state: {} }));
	},
};

export const NarrowViewport: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async ({ canvasElement }) => {
		await expectNoOverflowingElement(canvasElement);
	},
};
