import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, within } from "storybook/test";

import { eventLabel } from "@/components/practice-trace/fixtures";
import { withPageBehind } from "@/stories/decorators";
import { InLevelStack } from "@/stories/level-stack";
import { settledDrawerPanel } from "@/stories/overlay";

import { levelsOpenedBy } from "@/test/detail-stack";
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
const reviewItemEvents = itemEventsOf(reviewDetailCompleted);

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
		history.getByText(/The assigned AI model was unavailable\./u);
		await expect(history.getByRole("link", { name: "Open AI models" })).toHaveAttribute(
			"href",
			expect.stringContaining("/admin/models"),
		);
		panel.getByText("feature/retry-service → main");
		await expect(panel.getByRole("link", { name: /^Failing\b/u })).toHaveAttribute(
			"href",
			expect.stringContaining("/checks"),
		);
		await userEvent.click(panel.getByRole("button", { name: "Request review" }));
		await expect(args.onRequestReview).not.toHaveBeenCalled();
		const confirm = await screen.findByRole("alertdialog");
		await userEvent.click(within(confirm).getByRole("button", { name: "Request review" }));
		await expect(args.onRequestReview).toHaveBeenCalledOnce();
	},
};

/** Upgraded history: each event started a review it no longer links to. The header counts what the badges say. */
export const StartedEventsWithoutReviewLink: Story = {
	args: {
		events: {
			status: "ready",
			events: reviewItemEvents
				.slice(0, 2)
				.map((event) => ({ ...event, state: "STARTED" as const, reviewId: null })),
		},
	},
	play: async () => {
		const panelElement = await settledDrawerPanel();
		await expect(panelElement.querySelector("[data-sheet-status]")).toHaveTextContent(
			"2 events, 2 started a review",
		);
	},
};

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

export const PartOfAPullRequest: Story = {
	args: {
		item: { status: "ready", item: pullRequestReviewItem },
		events: { status: "ready", events: [] },
		facts: { status: "ready", facts: [] },
		onRequestReview: undefined,
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(
			levelsOpenedBy(panel.getByRole("link", { name: "Open the work it belongs to" })),
		).toEqual(["item:42"]);
		await expect(panel.queryByRole("button", { name: "Request review" })).toBeNull();
	},
};

export const NoEvents: Story = {
	args: { events: { status: "ready", events: [] } },
	play: async () => {
		const panelElement = await settledDrawerPanel();
		const panel = within(panelElement);
		panel.getByText("No event was recorded on this work.");
		await expect(panelElement.querySelector("[data-sheet-status]")).toBeNull();
	},
};

export const ProviderFactsFailed: Story = {
	args: { facts: { status: "error", error: new Error("Network down"), onRetry: fn() } },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText(/what the provider reports about this work/u);
		panel.getByRole("list", { name: "History" });
	},
};

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

export const LoadFailed: Story = {
	args: { item: { status: "error", error: new Error("Network down"), onRetry: retryItem } },
	play: async ({ userEvent }) => {
		const panel = within(await settledDrawerPanel());
		await userEvent.click(panel.getByRole("button", { name: "Retry" }));
		await expect(retryItem).toHaveBeenCalled();
	},
};
