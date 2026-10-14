import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, within } from "storybook/test";

import { eventLabel } from "@/components/practice-trace/fixtures";
import { withPageBehind } from "@/stories/decorators";
import { InLevelStack } from "@/stories/level-stack";
import { settledDrawerPanel } from "@/stories/overlay";

import {
	itemEventsOf,
	mergedPullRequestFacts,
	openIssueFacts,
	pullRequestReviewItem,
	reviewDetailCompleted,
	reviewItem,
	trackerIssue,
} from "./fixtures";
import { SHEET_SECTIONS } from "./RecordSheet";
import { itemLevel } from "./review-levels";
import { WorkLevel } from "./WorkLevel";

const retryItem = fn();
/** The pull request's events: one started its review, one waited out a cooldown, one was blocked. */
const reviewItemEvents = itemEventsOf(reviewDetailCompleted);

/**
 * One item's sheet, in the order every record sheet has: a status line (what became of its latest
 * event, how many events started a review, the latest), What it is (kind, where it lives, the work
 * it belongs to, and what its provider reports), its history — every event on the item with how we
 * came to know, what became of it, why no review started and the setting that would let it
 * through — Technical (closed) and asking for a review as the footer.
 */
const meta = {
	component: WorkLevel,
	parameters: { layout: "fullscreen" },
	decorators: [withPageBehind],
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		path: { behind: [{ label: "Practice reviews", depth: 0 }], onClose: fn() },
		item: { status: "ready", item: reviewItem },
		events: { status: "ready", events: reviewItemEvents },
		facts: { status: "ready", facts: mergedPullRequestFacts.facts },
		eventLabel,
		onRequestReview: fn(),
	},
	argTypes: { path: { control: false }, eventLabel: { control: false } },
	render: (args) => (
		<InLevelStack entry={itemLevel(reviewItem.id)} path={args.path}>
			{(level) => <WorkLevel {...args} {...level} />}
		</InLevelStack>
	),
} satisfies Meta<typeof WorkLevel>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Every event on the pull request, oldest first, with what became of each, and what its provider
 * reports about it.
 */
export const WithEvents: Story = {
	play: async ({ args, userEvent }) => {
		const panelElement = await settledDrawerPanel();
		const panel = within(panelElement);
		await expect(
			panel.getAllByRole("heading", { level: 3 }).map((heading) => heading.textContent),
		).toStrictEqual([SHEET_SECTIONS.what, SHEET_SECTIONS.state, SHEET_SECTIONS.technical]);
		const history = within(panel.getByRole("list", { name: "History" }));
		await expect(history.getAllByRole("listitem")).toHaveLength(reviewItemEvents.length);
		await expect(panelElement.querySelector("[data-sheet-status]")).toHaveTextContent(
			`${reviewItemEvents.length} events, 1 started a review`,
		);
		// Why an event started no review, with the setting that would let it through.
		history.getByText(/The assigned AI model was unavailable\./u);
		await expect(history.getByRole("link", { name: "Open AI models" })).toHaveAttribute(
			"href",
			expect.stringContaining("/admin/models"),
		);
		// What the provider reports, linked where it has a page.
		panel.getByText("feature/retry-service → main");
		await expect(panel.getByRole("link", { name: "Failing" })).toHaveAttribute(
			"href",
			expect.stringContaining("/checks"),
		);
		await userEvent.click(panel.getByRole("button", { name: "Request review" }));
		// It starts a review, so it asks first.
		await expect(args.onRequestReview).not.toHaveBeenCalled();
		const confirm = await screen.findByRole("alertdialog");
		await userEvent.click(within(confirm).getByRole("button", { name: "Request review" }));
		await expect(args.onRequestReview).toHaveBeenCalledOnce();
	},
};

/** While a review of the item is queued or running, the footer says so and asks nothing. */
export const RequestBlockedWhileRunning: Story = {
	args: { requestBlocked: "A review of this work is already queued or running." },
	play: async ({ args, userEvent }) => {
		const panel = within(await settledDrawerPanel());
		const button = panel.getByRole("button", { name: "Request review" });
		await expect(button).toHaveAttribute("aria-disabled", "true");
		await userEvent.click(button);
		await expect(args.onRequestReview).not.toHaveBeenCalled();
	},
};

/** An issue reports only what its provider has for it: no branches, size or checks. */
export const IssueFacts: Story = {
	args: {
		item: { status: "ready", item: trackerIssue },
		events: { status: "ready", events: [] },
		facts: { status: "ready", facts: openIssueFacts.facts },
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText("grace");
		await expect(panel.queryByText("Branch")).toBeNull();
		await expect(panel.queryByText("Checks")).toBeNull();
	},
};

/** A pull request review is its own item, and links to the pull request it belongs to. */
export const PartOfAPullRequest: Story = {
	args: {
		item: { status: "ready", item: pullRequestReviewItem },
		events: { status: "ready", events: [] },
		facts: { status: "ready", facts: [] },
		onRequestReview: undefined,
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.getByRole("link", { name: "Open the work it belongs to" })).toHaveAttribute(
			"href",
			expect.stringContaining(`item%3A${reviewItem.id}`),
		);
		await expect(panel.queryByRole("button", { name: "Request review" })).toBeNull();
	},
};

/** No event was recorded on the item: the history says so instead of an empty list. */
export const NoEvents: Story = {
	args: { events: { status: "ready", events: [] } },
	play: async () => {
		const panelElement = await settledDrawerPanel();
		const panel = within(panelElement);
		panel.getByText("No event was recorded on this work.");
		await expect(panelElement.querySelector("[data-sheet-status]")).toBeNull();
	},
};

/** What the provider reports could not be read: the events still stand. */
export const ProviderFactsFailed: Story = {
	args: { facts: { status: "error", error: new Error("Network down"), onRetry: fn() } },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText(/what the provider reports about this work/u);
		panel.getByRole("list", { name: "History" });
	},
};

/** While the item is read, the sheet's shape stands in for it. */
export const Loading: Story = {
	args: {
		item: { status: "loading" },
		events: { status: "loading" },
		facts: { status: "loading" },
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.queryByRole("heading", { name: SHEET_SECTIONS.what })).toBeNull();
	},
};

/** The item could not be read: the sheet says so and offers to try again. */
export const LoadFailed: Story = {
	args: { item: { status: "error", error: new Error("Network down"), onRetry: retryItem } },
	play: async ({ userEvent }) => {
		const panel = within(await settledDrawerPanel());
		await userEvent.click(panel.getByRole("button", { name: "Retry" }));
		await expect(retryItem).toHaveBeenCalled();
	},
};
