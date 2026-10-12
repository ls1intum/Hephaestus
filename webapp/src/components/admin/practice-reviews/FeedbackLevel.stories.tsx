import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, waitFor, within } from "storybook/test";

import type { ReviewFeedbackDetail } from "@/api/types.gen";
import { withPageBehind } from "@/stories/decorators";
import { InLevelStack } from "@/stories/level-stack";
import { expectSettledVisible, settledDrawerPanel } from "@/stories/overlay";
import { expectNoPanelOverflow } from "@/stories/reflow";
import { daysBefore, hoursBefore, minutesAfter } from "@/stories/story-clock";
import { expectGenuinelyDisabled } from "@/test/controls";
import { levelsOpenedBy } from "@/test/detail-stack";
import { precedes } from "@/test/dom";

import { FeedbackLevel } from "./FeedbackLevel";
import {
	feedbackDetail,
	longFeedbackDetail,
	POSTED_SUMMARY_COMMENT,
	reviewArtifact,
	reviewFeedbackDetail,
} from "./fixtures";
import { SHEET_SECTIONS } from "./RecordSheet";
import { feedbackLevel } from "./review-levels";

const ready = (feedback: ReviewFeedbackDetail) => ({ status: "ready" as const, feedback });

const delivered = feedbackDetail("99999999-6666-6666-6666-666666666666");

/** A card shown on the developer's practice pages; its text is theirs alone. */
const practicePageCard: ReviewFeedbackDetail = {
	...delivered,
	channel: "IN_APP",
	location: "PRACTICE_PAGES",
	deliveryState: "DELIVERED",
	body: undefined,
	placements: [],
	dispatches: [],
};

const partiallyDelivered: ReviewFeedbackDetail = {
	...delivered,
	deliveryState: "PARTIALLY_DELIVERED",
	deliveredAt: undefined,
	placements: [{ id: "summary-placement", placementType: "SUMMARY", postedCommentRef: "2481933" }],
	// The line comment did not go through yet: the approved package is tried again.
	dispatches: [
		{
			id: "d15c0000-0000-4000-8000-000000000001",
			destination: "APPROVED_REVIEW_PACKAGE",
			state: "PENDING",
			attemptCount: 2,
			nextAttemptAt: minutesAfter(10),
			lastError: "GitHub answered 502 Bad Gateway while the line comment was posted.",
			createdAt: hoursBefore(5),
			updatedAt: hoursBefore(4),
		},
	],
	proposedPlacements: [
		{ type: "SUMMARY", body: "Review summary" },
		{
			type: "INLINE",
			body: "Use the established retry boundary here.",
			path: "server/src/main/java/example/LongProviderBoundaryName.java",
			startLine: 118,
		},
	],
	approval: {
		decision: "APPROVED",
		actorAccountId: 7,
		decidedBy: "Ada Admin",
		decidedAt: hoursBefore(5),
	},
};

const rejected: ReviewFeedbackDetail = {
	...partiallyDelivered,
	deliveryState: "DISCARDED",
	placements: [],
	dispatches: [],
	approval: {
		decision: "REJECTED",
		actorAccountId: 8,
		decidedBy: "Grace Admin",
		decidedAt: hoursBefore(4),
		rejectionReason: "MISSING_CONTEXT",
		rejectionNote: "The review did not account for the provider's retry contract.",
	},
};

/** A proposal: the exact summary and line comments approval would send, written against a revision. */
const awaitingApproval: ReviewFeedbackDetail = {
	...reviewFeedbackDetail,
	reviewedWork: reviewArtifact.reviewedWork,
	deliveryState: "AWAITING_APPROVAL",
	deliveredAt: undefined,
	suppressionReason: undefined,
	placements: [],
	proposedPlacements: [
		{ type: "SUMMARY", body: reviewFeedbackDetail.body ?? "Review summary" },
		{
			type: "INLINE",
			body: "Catch the expected transport error and let programming errors surface.",
			path: "src/main/java/example/RetryService.java",
			startLine: 48,
		},
		{
			type: "INLINE",
			body: "Explain why this branch changes behavior.",
			path: "src/main/java/example/ReviewHandler.java",
			startLine: 91,
			endLine: 94,
		},
	],
	reviewedRevision: "27f4e88c9f5a",
};

/**
 * One piece of feedback's sheet, over the list it was opened from or over a trace, in the order
 * every record sheet has: a status line, What it is, State and history (composed, decided,
 * withheld with its precise reason, delivered with each comment's permalink, withdrawn, disputed),
 * what it says and its own delivery checks, Technical (closed) and the actions. It is titled by its
 * first line, as its row is. It links to no other record: the observations it was written from
 * are its trace.
 *
 * Feedback awaiting approval is the same level: the package is expanded, because approving it
 * unread is the failure to design out, and the decision is the footer like every sheet's actions.
 */
const meta = {
	component: FeedbackLevel,
	parameters: { layout: "fullscreen" },
	decorators: [withPageBehind],
	tags: ["autodocs"],
	args: {
		path: { behind: [{ label: "Practice reviews", depth: 0 }], onClose: fn() },
		feedback: ready(reviewFeedbackDetail),
		isDeciding: false,
		onApprove: fn(),
		onReject: fn(),
		onChangeWithdrawal: fn(async () => undefined),
		isChangingWithdrawal: false,
	},
	argTypes: { path: { control: false } },
	render: (args) => (
		<InLevelStack entry={feedbackLevel(reviewFeedbackDetail.id)} path={args.path}>
			{(level) => <FeedbackLevel {...args} {...level} />}
		</InLevelStack>
	),
} satisfies Meta<typeof FeedbackLevel>;

export default meta;
type Story = StoryObj<typeof meta>;

/** A chip in the header: what became of the feedback, under the title, where standing goes. */
async function expectHeaderChip(panel: HTMLElement, label: string) {
	const [chip] = within(panel).getAllByText(label);
	if (!chip) {
		throw new Error(`No "${label}" on the level`);
	}
	await expect(precedes(within(panel).getByRole("heading", { level: 2 }), chip)).toBe(true);
}

export const NotDelivered: Story = {
	play: async () => {
		const panelElement = await settledDrawerPanel();
		const panel = within(panelElement);
		// Titled by its first line, as its row on the Feedback list is.
		await expect(panel.getByRole("heading", { level: 2 })).not.toHaveTextContent(/^Feedback for /u);
		await expectHeaderChip(panelElement, "Withheld");
		await expect(
			panel.getAllByRole("heading", { level: 3 }).map((heading) => heading.textContent),
		).toStrictEqual([
			SHEET_SECTIONS.what,
			SHEET_SECTIONS.state,
			"What it says",
			SHEET_SECTIONS.technical,
		]);
		// The sheet is the feedback alone: no story, and no link to another record or list.
		await expect(
			panel.queryByRole("group", { name: "How this came about" }),
		).not.toBeInTheDocument();
		for (const link of panel.queryAllByRole("link")) {
			await expect(String(link.getAttribute("href"))).not.toMatch(/detail=|\/reviews\//u);
		}
		panel.getByText("Found while reviewing past work, which is measured but never sent.");
		// A record, not a proposal: nothing is left to decide.
		await expect(
			panel.queryByRole("button", { name: "Approve for delivery" }),
		).not.toBeInTheDocument();
	},
};

/** A comment's permalink is the sheet's one link out besides the work. */
export const DeliveredWithPermalink: Story = {
	args: {
		feedback: ready({
			...delivered,
			placements: delivered.placements.map((placement) => ({
				...placement,
				postedCommentUrl: `https://github.com/ls1intum/Hephaestus/pull/1431#discussion_r${placement.postedCommentRef ?? "1"}`,
			})),
		}),
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		const [link] = panel.getAllByRole("link", { name: /Open the comment/u });
		await expect(link).toHaveAttribute("href", expect.stringContaining("#discussion_r"));
		// The comment links there, so its delivery does not link there a second time.
		await expect(panel.queryByRole("link", { name: "Open what was posted" })).toBeNull();
	},
};

export const Delivered: Story = {
	args: { feedback: ready(delivered) },
	play: async () => {
		const panelElement = await settledDrawerPanel();
		const panel = within(panelElement);
		await expectHeaderChip(panelElement, "Delivered");
		// Each comment where it was posted, once, in the history.
		panel.getByText("As an inline note");
		panel.getByText("server/application/src/main/resources/application.yml:118–120");
		// Its sent delivery is that "Delivered" event, not a second event saying it was sent; the
		// link to what it posted joins the event.
		const history = within(panel.getByRole("list", { name: "History" }));
		await expect(history.queryByText("Sent")).toBeNull();
		// Composed and Delivered, each one event; the comments it lists are not events.
		await expect(panelElement.querySelectorAll("[data-sheet-event]")).toHaveLength(2);
		history.getByRole("link", { name: /Open what was posted/u });
		// Where it went, by provider: once, among What it is.
		panel.getByText(/^Comment on Git(?:Hub|Lab) (?:pull|merge) request/u);
		await expect(panel.queryByText(/^On Git(?:Hub|Lab)$/u)).toBeNull();
		await expect(panel.queryByRole("button", { name: "Reject feedback" })).not.toBeInTheDocument();
		// Only a practice-page card can be withdrawn; a note on the work offers no such action.
		await expect(
			panel.queryByRole("button", { name: "Withdraw feedback" }),
		).not.toBeInTheDocument();
	},
};

/**
 * A summary comment on the pull request: "What it says" shows the posted text as the developer saw
 * it on GitHub, without the hidden marker and with the footer as small print.
 */
export const DeliveredSummaryComment: Story = {
	args: {
		feedback: ready({ ...delivered, body: POSTED_SUMMARY_COMMENT, proposedPlacements: [] }),
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		const rendered = panel.getByRole("tabpanel", { name: "Rendered" });
		await expect(rendered.textContent).toContain("The description needs its purpose and coverage.");
		await expect(rendered.textContent).not.toMatch(/<!--|<sub>/u);
		await expect(
			within(rendered).getByRole("link", { name: "Why you see this and how to stop it" }),
		).toBeVisible();
	},
};

/**
 * On GitLab a note proposed for some lines is posted as a comment on the merge request that links to
 * them, and the sheet says so rather than calling it an inline note.
 */
export const DeliveredAsCommentsLinkingToLines: Story = {
	args: {
		feedback: ready({
			...delivered,
			placements: [
				{ id: "p1", placementType: "SUMMARY", postedCommentRef: "2481933" },
				{ id: "p2", placementType: "LOCATION_COMMENT", postedCommentRef: "2481934" },
			],
		}),
	},
	play: async () => {
		const panelElement = await settledDrawerPanel();
		await expect(
			within(panelElement).getAllByText(/As a comment linking to the lines/u).length,
		).toBeGreaterThan(0);
		await expect(panelElement.textContent).not.toContain("As an inline note");
	},
};

/** The change got a new push after its review, so what was not yet posted stayed back. */
export const WithheldAfterTheChangeWasUpdated: Story = {
	args: {
		feedback: ready({
			...delivered,
			deliveryState: "SUPPRESSED",
			suppressionReason: "REVIEWED_REVISION_CHANGED",
			deliveredAt: undefined,
			placements: [],
			dispatches: [],
		}),
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(
			panel.getAllByText("The change was updated before this feedback could be posted.").length,
		).toBeGreaterThan(0);
	},
};

/** The reason is required: a withdrawal nobody can explain later is not an audit. */
export const WithdrawFeedback: Story = {
	args: { feedback: ready(practicePageCard) },
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, userEvent }) => {
		const panel = within(await settledDrawerPanel());
		// The text is private to the developer, and is not reported as never composed.
		panel.getByText(
			/written for the developer’s own practice pages and is withheld from operators/u,
		);
		panel.getByText("Card on their practice pages");
		await userEvent.click(panel.getByRole("button", { name: "Withdraw feedback" }));
		const reason = await screen.findByRole("textbox", { name: "Reason" });
		await expectSettledVisible(reason);
		const submit = screen.getAllByRole("button", { name: "Withdraw feedback" }).at(-1);
		await expect(submit).toBeDisabled();
		await userEvent.type(reason, "  It describes issues from before this pull request.  ");
		if (submit) {
			await userEvent.click(submit);
		}
		await expect(args.onChangeWithdrawal).toHaveBeenCalledWith(
			true,
			"It describes issues from before this pull request.",
		);
		await waitFor(async () => expect(screen.queryByRole("textbox", { name: "Reason" })).toBeNull());
	},
};

/** A refused withdrawal keeps the form open with the reason the admin wrote, ready for another try. */
export const WithdrawFeedbackFails: Story = {
	args: {
		feedback: ready(practicePageCard),
		onChangeWithdrawal: fn(async () => {
			throw new Error("409");
		}),
	},
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, userEvent }) => {
		const panel = within(await settledDrawerPanel());
		await userEvent.click(panel.getByRole("button", { name: "Withdraw feedback" }));
		const reason = await screen.findByRole("textbox", { name: "Reason" });
		await expectSettledVisible(reason);
		await userEvent.type(reason, "It describes older issues.");
		const submit = screen.getAllByRole("button", { name: "Withdraw feedback" }).at(-1);
		if (submit) {
			await userEvent.click(submit);
		}
		await expect(args.onChangeWithdrawal).toHaveBeenCalledOnce();
		await expect(screen.getByRole("textbox", { name: "Reason" })).toHaveValue(
			"It describes older issues.",
		);
	},
};

/** A disputed card shows the developer's explanation above what became of it, never the card's text. */
export const Disputed: Story = {
	args: {
		feedback: ready({
			...practicePageCard,
			dispute: {
				feedbackId: practicePageCard.id,
				channel: "IN_APP",
				explanation: "This was about a branch I did not write.",
				disputedAt: hoursBefore(1),
			},
		}),
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText("Disputed by the developer");
		panel.getByText("This was about a branch I did not write.");
		await expect(panel.queryByRole("alert")).toBeNull();
		panel.getByText(
			/written for the developer’s own practice pages and is withheld from operators/u,
		);
		panel.getByRole("button", { name: "Withdraw feedback" });
	},
};

/** Withdrawn again after a restore: the level offers the restore, and the earlier round stays listed. */
export const Withdrawn: Story = {
	args: {
		feedback: ready({
			...practicePageCard,
			withdrawals: [
				{
					id: "wd-2",
					reason: "It describes issues from before this pull request.",
					withdrawnAt: hoursBefore(2),
					withdrawnBy: "Ada Admin",
				},
				{
					id: "wd-1",
					reason: "Looked wrong at first.",
					withdrawnAt: daysBefore(3),
					withdrawnBy: "Ada Admin",
					restorationReason: "It was about this pull request after all.",
					restoredAt: daysBefore(2),
				},
			],
		}),
	},
	play: async () => {
		const panelElement = await settledDrawerPanel();
		const panel = within(panelElement);
		await expectHeaderChip(panelElement, "Withdrawn");
		panel.getByText(/It is off the developer’s practice pages/u);
		await expect(panel.queryByRole("alert")).toBeNull();
		await expect(panel.getByRole("button", { name: "Restore feedback" })).toBeEnabled();
		const history = within(panel.getByRole("list", { name: "History" }));
		await expect(history.getAllByText("Withdrawn")).toHaveLength(2);
		await expect(history.getAllByText("Restored")).toHaveLength(1);
		// The erased restorer is named as such, not left blank.
		await expect(history.getByText(/an account that no longer exists/u)).toBeVisible();
	},
};

export const PartiallyDelivered: Story = {
	args: { feedback: ready(partiallyDelivered) },
	play: async () => {
		const panelElement = await settledDrawerPanel();
		const panel = within(panelElement);
		await expect(panelElement.querySelector("[data-sheet-status]")).toHaveTextContent(
			/1 of 2 delivered/u,
		);
		panel.getByText("Approved");
		// Named, not "Account 7".
		panel.getByText("by Ada Admin");
		await expect(panel.queryByText(/Account 7/u)).toBeNull();
		// Its delivery: how often it was tried, when next, and the provider's last answer.
		const history = within(panel.getByRole("list", { name: "History" }));
		history.getByText("Waiting to be sent");
		history.getByText(/The approved comments · 2 attempts/u);
		history.getByText(/Next attempt/u);
		history.getByText(/502 Bad Gateway/u);
	},
};

/**
 * A delivery that gave up says how often it was tried and what the provider last answered, under
 * the outcome rather than as a second outcome.
 */
export const DeliveryFailed: Story = {
	args: {
		feedback: ready({
			...delivered,
			deliveryState: "FAILED",
			deliveredAt: undefined,
			placements: [],
			dispatches: [
				{
					id: "d15c0000-0000-4000-8000-000000000002",
					destination: "AUTOMATIC_REVIEW_PACKAGE",
					state: "FAILED",
					attemptCount: 5,
					lastError: "GitHub answered 403: Resource not accessible by integration.",
					createdAt: hoursBefore(6),
					updatedAt: hoursBefore(5),
				},
			],
		}),
	},
	play: async () => {
		const panelElement = await settledDrawerPanel();
		const panel = within(panelElement);
		// The outcome once, on the status line; the history says what became of the delivery and why,
		// not "Failed" a second time.
		await expectHeaderChip(panelElement, "Failed to deliver");
		const history = within(panel.getByRole("list", { name: "History" }));
		history.getByText("Stopped trying");
		await expect(history.queryByText(/Failed/u)).toBeNull();
		history.getByText("The review’s comments · 5 attempts");
		history.getByText(/Resource not accessible by integration/u);
		// Nothing is tried again, so no next attempt.
		await expect(history.queryByText(/Next attempt/u)).toBeNull();
		panel.getByText("d15c0000-0000-4000-8000-000000000002");
	},
};

export const Rejected: Story = {
	args: { feedback: ready(rejected) },
	play: async () => {
		const panelElement = await settledDrawerPanel();
		const panel = within(panelElement);
		await expectHeaderChip(panelElement, "Rejected");
		// Said once, in the status line; the history names who decided and why.
		await expect(panel.getAllByText("Rejected")).toHaveLength(2);
		panel.getByText("by Grace Admin");
		// The category is the registry's words, not the stored value.
		await expect(panel.getByText(/Missing important context/u)).toBeVisible();
		panel.getByText(/The review did not account for the provider's retry contract/u);
	},
};

/**
 * The longest realistic record, at the narrowest width: a rendered body with code, and every
 * observation it was based on.
 */
export const Reflow: Story = {
	args: { feedback: ready(longFeedbackDetail) },
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async () => {
		await expectNoPanelOverflow(await settledDrawerPanel());
	},
};

/** Long feedback reads whole, Rendered or as its Source. */
export const LongFeedback: Story = {
	args: { feedback: ready(longFeedbackDetail) },
	play: async ({ userEvent }) => {
		const panel = within(await settledDrawerPanel());
		const rendered = panel.getByRole("tabpanel", { name: "Rendered" });
		await expect(within(rendered).getByText(/2 issues to tighten in this change/u)).toBeVisible();
		await userEvent.click(panel.getByRole("tab", { name: "Source" }));
		await expect(panel.getByRole("tabpanel", { name: "Source" }).textContent).toContain("```java");
	},
};

export const PreparedForConversation: Story = {
	args: { feedback: ready(feedbackDetail("11111111-4444-4444-4444-444444444444")) },
	play: async () => {
		const panelElement = await settledDrawerPanel();
		await expectHeaderChip(panelElement, "Prepared for conversation");
		within(panelElement).getByText("For a conversation with Heph");
		// The conversation names it in the facts.
		within(panelElement).getAllByText("#engineering");
	},
};

/**
 * A practice pages card not opened yet. Nothing is retried on this channel, so the sheet says the
 * developer opening it is the delivery, never that comments are tried again.
 */
export const PreparedForPracticePages: Story = {
	args: {
		feedback: ready({ ...practicePageCard, deliveryState: "PREPARED", deliveredAt: undefined }),
	},
	play: async () => {
		const panelElement = await settledDrawerPanel();
		await expectHeaderChip(panelElement, "Prepared for their practice pages");
		await expect(
			within(panelElement).getByText(/counts as delivered when they open it/u),
		).toBeVisible();
		await expect(within(panelElement).queryByText(/tried again/u)).toBeNull();
	},
};

/**
 * Awaiting approval, the level leads with what approval authorizes and ends in the decision. What
 * will be sent is expanded — one summary and every line comment — rather than behind a disclosure.
 */
export const AwaitingApproval: Story = {
	args: { feedback: ready(awaitingApproval) },
	play: async ({ args, userEvent }) => {
		const panel = within(await settledDrawerPanel());
		await expect(
			panel.getByText(/Approval authorizes the summary and every line comment above/u),
		).toBeVisible();
		await expect(panel.getByText("1 summary and 2 line comments")).toBeVisible();
		await expect(panel.getByText("src/main/java/example/RetryService.java")).toBeVisible();
		await expect(panel.getByText("27f4e88c9f5a")).toBeVisible();
		// The record's own sections wait until there is a record: nothing has become of it yet.
		await expect(
			panel.queryByRole("heading", { name: "What became of it" }),
		).not.toBeInTheDocument();

		// The decision is the footer, like every sheet's actions, so it stays in reach under the package.
		const approve = panel.getByRole("button", { name: "Approve for delivery" });
		const reject = panel.getByRole("button", { name: "Reject feedback" });
		await expect(approve.closest('[data-slot="drawer-footer"]')).not.toBeNull();
		await expect(reject.closest('[data-slot="drawer-footer"]')).not.toBeNull();
		await userEvent.click(approve);
		await expect(args.onApprove).toHaveBeenCalledOnce();
	},
};

/**
 * On GitLab the line notes go up as comments on the merge request that link to their lines, so the
 * preview says that, while the package below still shows each note at the lines it was written for.
 */
export const AwaitingApprovalOnGitLab: Story = {
	args: {
		feedback: ready({
			...awaitingApproval,
			reviewedWork: {
				id: "425",
				kind: "scm.pull_request",
				label: "!425",
				provider: "GITLAB",
				container: "acme/api",
			},
		}),
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.getByText("1 summary and 2 comments linking to lines")).toBeVisible();
		panel.getByText("As a comment linking to the lines on the work");
		await expect(panel.queryByText("As an inline note on the work")).not.toBeInTheDocument();
		await expect(panel.getByText("src/main/java/example/RetryService.java")).toBeVisible();
	},
};

export const AwaitingApprovalReflow: Story = {
	args: { feedback: ready(awaitingApproval) },
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async () => {
		const panel = await settledDrawerPanel();
		await expect(within(panel).getByRole("button", { name: "Approve for delivery" })).toBeVisible();
		await expectNoPanelOverflow(panel);
	},
};

const PREVIOUS_ID = "aaaaaaaa-0000-4000-8000-000000000001";
const NEXT_ID = "aaaaaaaa-0000-4000-8000-000000000003";

/**
 * Opened from the approval queue: where it sits among all awaiting approval, oldest first, a step
 * either way that swaps this level for its neighbour, and a decision that moves on to the next.
 */
export const InApprovalQueue: Story = {
	args: {
		feedback: ready(awaitingApproval),
		queue: { position: 2, total: 7, previous: PREVIOUS_ID, next: NEXT_ID },
	},
	play: async ({ args, userEvent }) => {
		const panel = within(await settledDrawerPanel());
		const steps = within(panel.getByRole("navigation", { name: "Feedback awaiting approval" }));
		steps.getByText("2 of 7");
		// A step replaces the level in front rather than stacking one over it.
		await expect(levelsOpenedBy(steps.getByRole("link", { name: "Previous" }))).toEqual([
			`feedback:${PREVIOUS_ID}`,
		]);
		await expect(levelsOpenedBy(steps.getByRole("link", { name: "Next" }))).toEqual([
			`feedback:${NEXT_ID}`,
		]);
		await userEvent.click(panel.getByRole("button", { name: "Approve and open next" }));
		await expect(args.onApprove).toHaveBeenCalledOnce();
	},
};

/**
 * The last in the queue, with no step forward — but the ones before it may have been skipped on the
 * way here and still wait, so a decision moves on to them and the button does not promise to close.
 */
export const LastInApprovalQueue: Story = {
	args: {
		feedback: ready(awaitingApproval),
		queue: { position: 7, total: 7, previous: PREVIOUS_ID },
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText("7 of 7");
		await expectGenuinelyDisabled(panel.getByRole("button", { name: "Next" }));
		panel.getByRole("button", { name: "Approve and open next" });
	},
};

/** The only one awaiting approval: no steps to take, and approving it closes the level. */
export const OnlyInApprovalQueue: Story = {
	args: {
		feedback: ready(awaitingApproval),
		queue: { position: 1, total: 1 },
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(
			panel.queryByRole("navigation", { name: "Feedback awaiting approval" }),
		).not.toBeInTheDocument();
		panel.getByRole("button", { name: "Approve and close" });
	},
};

/**
 * Whether `first` reads before `second` in a wrapping row: on a line above it, or on the same line
 * and to its left. How the footer wraps at 320px depends on the font's widths: with the system font
 * on macOS the steps take a line of their own, with Arial's widths they share one with "Reject
 * feedback", and either reads the steps first.
 */
function readsBefore(first: HTMLElement, second: HTMLElement): boolean {
	const a = first.getBoundingClientRect();
	const b = second.getBoundingClientRect();
	const SLACK_PX = 1;
	if (a.bottom <= b.top + SLACK_PX) {
		return true;
	}
	const sameLine = a.top < b.bottom && b.top < a.bottom;
	return sameLine && a.right <= b.left + SLACK_PX;
}

export const InApprovalQueueReflow: Story = {
	args: InApprovalQueue.args,
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async () => {
		const panel = await settledDrawerPanel();
		await expect(
			within(panel).getByRole("button", { name: "Approve and open next" }),
		).toBeVisible();
		await expectNoPanelOverflow(panel);
		// The wrapped footer shows the steps first, as the tab order reaches them first.
		const steps = within(panel).getByRole("navigation", { name: "Feedback awaiting approval" });
		for (const name of ["Approve and open next", "Reject feedback"]) {
			await expect(
				readsBefore(steps, within(panel).getByRole("button", { name })),
				`The steps read after "${name}"`,
			).toBe(true);
		}
	},
};

/** A proposal with no package has nothing to authorize, so only rejection is offered. */
export const PackageUnavailable: Story = {
	args: { feedback: ready({ ...awaitingApproval, proposedPlacements: [] }) },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.getByRole("alert")).toHaveTextContent("This review package is unavailable");
		await expectGenuinelyDisabled(panel.getByRole("button", { name: "Approve for delivery" }));
		await expect(panel.getByRole("button", { name: "Reject feedback" })).toBeEnabled();
	},
};

/** Nothing to check it against is worth saying, and is not a reason to withhold the decision. */
export const AwaitingApprovalWithoutObservations: Story = {
	args: { feedback: ready({ ...awaitingApproval, observations: [] }) },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.getByRole("button", { name: "Approve for delivery" })).toBeEnabled();
	},
};

export const RejectingWithContext: Story = {
	args: { feedback: ready(awaitingApproval) },
	play: async ({ args, userEvent }) => {
		const panel = within(await settledDrawerPanel());
		await userEvent.click(panel.getByRole("button", { name: "Reject feedback" }));
		const popover = await screen.findByRole("dialog", { name: "Reject this feedback" });
		const note = within(popover).getByLabelText("Note");
		await expectSettledVisible(note);
		// No category, no rejection: the reason is what the rejection is for.
		await expectGenuinelyDisabled(within(popover).getByRole("button", { name: "Reject feedback" }));
		await userEvent.click(within(popover).getByText("Missing important context"));
		await userEvent.type(note, "The fallback path is not represented in the review.");
		await userEvent.click(within(popover).getByRole("button", { name: "Reject feedback" }));
		await expect(args.onReject).toHaveBeenCalledWith(
			"MISSING_CONTEXT",
			"The fallback path is not represented in the review.",
		);
		await expect(args.onApprove).not.toHaveBeenCalled();
	},
};

export const Deciding: Story = {
	args: { feedback: ready(awaitingApproval), isDeciding: true },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expectGenuinelyDisabled(panel.getByRole("button", { name: "Approve for delivery" }));
		await expectGenuinelyDisabled(panel.getByRole("button", { name: "Reject feedback" }));
	},
};

/**
 * The drawer is named by its title, so the heading stands while the record loads; the path stays
 * usable, and no decision is offered about feedback not yet read.
 */
export const Loading: Story = {
	args: { feedback: { status: "loading" } },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.getByRole("heading", { level: 2 })).toHaveAccessibleName("Loading feedback");
		panel.getByRole("button", { name: "Practice reviews" });
		await expect(panel.queryByText("We could not load this feedback")).not.toBeInTheDocument();
		await expect(
			panel.queryByRole("button", { name: "Approve for delivery" }),
		).not.toBeInTheDocument();
	},
};

export const LoadFailed: Story = {
	args: {
		feedback: {
			status: "error",
			error: { status: 500 },
			onRetry: fn(),
		},
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.getByText("We could not load this feedback")).toBeVisible();
		// With no record to name it, the level is named for what it is.
		await expect(screen.getByRole("dialog")).toHaveAccessibleName("Feedback");
		await expect(panel.getByRole("button", { name: "Retry" })).toBeEnabled();
	},
};
