import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, waitFor, within } from "storybook/test";

import type { ReviewFeedbackDetail } from "@/api/types.gen";
import { withPageBehind } from "@/stories/decorators";
import { InLevelStack } from "@/stories/level-stack";
import { expectSettledVisible, settledDrawerPanel } from "@/stories/overlay";
import { expectNoPanelOverflow } from "@/stories/reflow";
import { expectGenuinelyDisabled } from "@/test/controls";
import { levelsOpenedBy } from "@/test/detail-stack";
import { precedes } from "@/test/dom";

import { FeedbackLevel } from "./FeedbackLevel";
import {
	approvedFeedbackDetail,
	awaitingApprovalFeedbackDetail,
	failedFeedbackDetail,
	feedbackDetail,
	longFeedbackDetail,
	markedIncorrectFeedbackDetail,
	partialPackageFeedbackDetail,
	POSTED_SUMMARY_COMMENT,
	PRIVATE_FEEDBACK_TEXT,
	rejectedFeedbackDetail,
	replacedFeedbackDetail,
	restoredFeedbackDetail,
	reviewFeedbackDetail,
	unconfirmedFeedbackDetail,
	uncertainWriteFeedbackDetail,
	withdrawnFeedbackDetail,
} from "./fixtures";
import { SHEET_SECTIONS } from "./RecordSheet";
import { feedbackLevel } from "./review-levels";

const ready = (feedback: ReviewFeedbackDetail) => ({ status: "ready" as const, feedback });

/** A card shown on the developer's practice pages; its text is theirs alone. */
const practicePageCard = feedbackDetail("44444444-5555-5555-5555-555555555555");

/**
 * One piece of feedback's sheet, over the list it was opened from or over a trace, in the order
 * every record sheet has: a status line, What it is, State and history (composed, each decision,
 * withheld with its precise reason, each copy posted with its permalink or failed with its error,
 * each send, the developer's response), what it says and its checks, Technical and the actions. It
 * is titled by its first line, as its row is. It links to no other record: the results it was
 * written from are its trace.
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

function history(panel: HTMLElement) {
	return within(within(panel).getByRole("list", { name: "History" }));
}

/** Found while reviewing past work, so it was measured and never sent. */
export const Withheld: Story = {
	play: async () => {
		const panelElement = await settledDrawerPanel();
		const panel = within(panelElement);
		// Titled by its first line, as its row on the Feedback list is.
		await expect(panel.getByRole("heading", { level: 2 })).toHaveTextContent(
			/^The public response exposes a name/u,
		);
		await expectHeaderChip(panelElement, "Withheld");
		const sections = [
			SHEET_SECTIONS.what,
			SHEET_SECTIONS.state,
			"What it says",
			"Checks",
			SHEET_SECTIONS.technical,
		];
		// In the order every sheet has; the checks' own rows are headings of their own.
		await expect(
			panel
				.getAllByRole("heading", { level: 3 })
				.map((heading) => heading.textContent)
				.filter((text) => sections.includes(text)),
		).toStrictEqual(sections);
		// The sheet is the feedback alone: no link to another record or list.
		for (const link of panel.queryAllByRole("link")) {
			await expect(String(link.getAttribute("href"))).not.toMatch(/detail=|\/reviews\//u);
		}
		history(panelElement).getByText(
			"Found while reviewing past work, which is measured but never sent.",
		);
		// A record, not a proposal: nothing is left to decide, and nothing was shown to withdraw.
		await expect(
			panel.queryByRole("button", { name: "Approve for delivery" }),
		).not.toBeInTheDocument();
		await expect(
			panel.queryByRole("button", { name: "Withdraw feedback" }),
		).not.toBeInTheDocument();
	},
};

/**
 * Approved, then posted on the pull request: the decision with who took it, the summary's receipt
 * with its permalink, and the developer's answer in their own words.
 */
export const Delivered: Story = {
	args: { feedback: ready(approvedFeedbackDetail) },
	play: async () => {
		const panelElement = await settledDrawerPanel();
		const panel = within(panelElement);
		await expectHeaderChip(panelElement, "Delivered");
		const events = history(panelElement);
		events.getByText("Approved");
		events.getByText("by Grace Hopper");
		events.getByText("Summary posted");
		await expect(events.getByRole("link", { name: /Open the comment/u })).toHaveAttribute(
			"href",
			expect.stringContaining("#issuecomment-"),
		);
		// A send that finished is said by the receipt, not a second time.
		await expect(events.queryByText("Done")).toBeNull();
		events.getByText("Answered");
		events.getByText("Helpful");
		// Where it went, by provider: once, among What it is.
		panel.getByText(/^Comment on GitHub pull request/u);
		await expect(panel.queryByRole("button", { name: "Reject feedback" })).not.toBeInTheDocument();
		await expect(panel.getByRole("button", { name: "Withdraw feedback" })).toBeEnabled();
	},
};

/**
 * A summary comment on the pull request: "What it says" shows the posted text as the developer saw
 * it on GitHub, without the hidden marker and with the footer as small print.
 */
export const DeliveredSummaryComment: Story = {
	args: { feedback: ready({ ...approvedFeedbackDetail, body: POSTED_SUMMARY_COMMENT }) },
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
 * The summary went up and both line notes failed: the status line counts the copies, each copy has
 * its receipt or its error, and the send says when it tries the failed ones again.
 */
export const PartialPackage: Story = {
	args: { feedback: ready(partialPackageFeedbackDetail) },
	play: async () => {
		const panelElement = await settledDrawerPanel();
		await expect(panelElement.querySelector("[data-sheet-status]")).toHaveTextContent(
			/1 posted · 2 failed/u,
		);
		const events = history(panelElement);
		events.getByText("Summary posted");
		await expect(events.getAllByText("Line note failed")).toHaveLength(2);
		events.getByText("ReviewQueryService.java:116–119");
		await expect(events.getAllByText(/the line is no longer part of the diff/u)).toHaveLength(2);
		events.getByText("Waiting to be sent");
		events.getByText("Post the copies · 2 attempts");
		events.getByText(/Next attempt/u);
		events.getByText(/422 for both line notes/u);
	},
};

/**
 * The developer disagreed: the flag says so, and their reason is in the history as theirs, never as
 * an admin's mark.
 */
export const Disagreed: Story = {
	args: { feedback: ready(longFeedbackDetail) },
	play: async () => {
		const panelElement = await settledDrawerPanel();
		await expectHeaderChip(panelElement, "Disagreed");
		const events = history(panelElement);
		events.getByText("Disagreed");
		events.getByText("The two cases share a cause, so one status is right here.");
		await expect(events.queryByText(/Marked incorrect/u)).toBeNull();
		within(panelElement).getByText(/While the developer’s disagreement stands/u);
	},
};

/** A response the developer cleared leaves no answer behind, and no disagreement. */
export const ClearedResponse: Story = {
	args: {
		feedback: ready({
			...longFeedbackDetail,
			disagreedAt: null,
			response: { resolution: null, usefulness: null, comment: null, createdAt: null },
		}),
	},
	play: async () => {
		const panelElement = await settledDrawerPanel();
		const events = history(panelElement);
		await expect(events.queryByText("Disagreed")).toBeNull();
		await expect(events.queryByText("Answered")).toBeNull();
		await expect(
			within(panelElement).queryByText(/While the developer’s disagreement stands/u),
		).toBeNull();
	},
};

/** A write started and the provider never confirmed it: the sheet says it is checked, not resent. */
export const UncertainWrite: Story = {
	args: { feedback: ready(uncertainWriteFeedbackDetail) },
	play: async () => {
		const panelElement = await settledDrawerPanel();
		await expectHeaderChip(panelElement, "Ready to send");
		const events = history(panelElement);
		events.getByText("Not confirmed");
		events.getByText("Summary writing");
		events.getByText(/The connection closed before GitHub answered/u);
		within(panelElement).getByText(
			"A write was not confirmed. The provider is checked before anything is posted again.",
		);
	},
};

/**
 * A send that gave up says how often it was tried and what the provider last answered, under the
 * outcome rather than as a second outcome.
 */
export const DeliveryFailed: Story = {
	args: { feedback: ready(failedFeedbackDetail) },
	play: async () => {
		const panelElement = await settledDrawerPanel();
		await expectHeaderChip(panelElement, "Failed to deliver");
		const events = history(panelElement);
		events.getByText("Stopped trying");
		events.getByText("Summary failed");
		events.getByText("Post the copies · 5 attempts");
		await expect(events.getAllByText(/Resource not accessible by integration/u).length).toBe(2);
		// Nothing is tried again, so no next attempt.
		await expect(events.queryByText(/Next attempt/u)).toBeNull();
	},
};

/** Withheld because a workspace admin marked the only result behind it incorrect. */
export const WithheldResultMarkedIncorrect: Story = {
	args: { feedback: ready(markedIncorrectFeedbackDetail) },
	play: async () => {
		const panelElement = await settledDrawerPanel();
		await expectHeaderChip(panelElement, "Withheld");
		history(panelElement).getByText(
			"A workspace admin marked a result behind it as incorrect, so nothing more of it was sent.",
		);
	},
};

/** The work changed before the copies went up, so they stayed back. */
export const WithheldAfterTheWorkChanged: Story = {
	args: {
		feedback: ready({
			...approvedFeedbackDetail,
			state: "WITHHELD",
			stopReason: "REVIEWED_VERSION_CHANGED",
			deliveredAt: null,
			copies: approvedFeedbackDetail.copies.map((copy) => ({
				...copy,
				state: "WITHHELD" as const,
				stopReason: "REVIEWED_VERSION_CHANGED" as const,
				postedAt: null,
				externalRef: null,
				externalUrl: null,
			})),
			sends: [],
			response: null,
		}),
	},
	play: async () => {
		const events = history(await settledDrawerPanel());
		// Said once: the copy's reason is the feedback's own.
		await expect(
			events.getAllByText("The work changed before this feedback could be posted."),
		).toHaveLength(1);
		events.getByText("Summary withheld");
	},
};

export const Replaced: Story = {
	args: { feedback: ready(replacedFeedbackDetail) },
	play: async () => {
		await expectHeaderChip(await settledDrawerPanel(), "Replaced by newer");
	},
};

export const Rejected: Story = {
	args: { feedback: ready(rejectedFeedbackDetail) },
	play: async () => {
		const panelElement = await settledDrawerPanel();
		await expectHeaderChip(panelElement, "Rejected");
		const events = history(panelElement);
		events.getByText("Rejected");
		events.getByText("by Grace Hopper");
		// The category is the registry's words, not the stored value.
		await expect(events.getByText(/Not useful to the developer/u)).toBeVisible();
		events.getByText(/The long comment already covers the test names/u);
	},
};

/** A decision whose account was erased names the account as gone, not as blank. */
export const DecidedByAnErasedAccount: Story = {
	args: {
		feedback: ready({
			...rejectedFeedbackDetail,
			decisions: rejectedFeedbackDetail.decisions.map((decision) => ({
				...decision,
				actor: null,
			})),
		}),
	},
	play: async () => {
		await expect(
			history(await settledDrawerPanel()).getByText(/an account that no longer exists/u),
		).toBeVisible();
	},
};

/** Withdrawn from the practice pages: the level offers the restore, and says what withdrawal did. */
export const Withdrawn: Story = {
	args: { feedback: ready(withdrawnFeedbackDetail) },
	play: async () => {
		const panelElement = await settledDrawerPanel();
		const panel = within(panelElement);
		await expectHeaderChip(panelElement, "Withdrawn");
		panel.getByText(/It is off the developer’s practice pages/u);
		await expect(panel.queryByRole("alert")).toBeNull();
		await expect(panel.getByRole("button", { name: "Restore feedback" })).toBeEnabled();
		const events = history(panelElement);
		events.getByText("Withdrawn");
		events.getByText(/Already covered elsewhere/u);
	},
};

/** Withdrawn, then restored: both decisions stay listed, and it can be withdrawn again. */
export const Restored: Story = {
	args: { feedback: ready(restoredFeedbackDetail) },
	play: async () => {
		const panelElement = await settledDrawerPanel();
		const events = history(panelElement);
		events.getByText("Withdrawn");
		events.getByText("Restored");
		events.getByText(/The result stands after all/u);
		await expect(
			within(panelElement).getByRole("button", { name: "Withdraw feedback" }),
		).toBeEnabled();
	},
};

/** The reason is required: a withdrawal nobody can explain later is not an audit. */
export const WithdrawFeedback: Story = {
	args: { feedback: ready(practicePageCard) },
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, userEvent }) => {
		const panelElement = await settledDrawerPanel();
		const panel = within(panelElement);
		// The text is private to the developer, and is not reported as never composed.
		panel.getByText(/written for the developer’s own practice pages and is withheld from admins/u);
		await expect(panelElement.textContent).not.toContain(PRIVATE_FEEDBACK_TEXT);
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

/**
 * A conversation linked it, and no appearance is recorded, so whether the developer saw it is
 * unknown: never counted as delivered, and never as withheld.
 */
export const DeliveryUnconfirmed: Story = {
	args: { feedback: ready(unconfirmedFeedbackDetail) },
	play: async () => {
		const panelElement = await settledDrawerPanel();
		await expectHeaderChip(panelElement, "Delivery unconfirmed");
		within(panelElement).getByText(/so it does not count as delivered/u);
		await expect(history(panelElement).queryByText("Delivered")).toBeNull();
	},
};

export const ReadyForConversation: Story = {
	args: { feedback: ready(feedbackDetail("11111111-4444-4444-4444-444444444444")) },
	play: async () => {
		const panelElement = await settledDrawerPanel();
		await expectHeaderChip(panelElement, "Ready for conversation");
		within(panelElement).getByText("For a conversation with Heph");
		within(panelElement).getByText(/which is what delivers it/u);
	},
};

/**
 * A practice pages card not opened yet. Nothing is retried at this destination, so the sheet says
 * the developer opening it is the delivery, never that copies are tried again.
 */
export const ReadyForPracticePages: Story = {
	args: {
		feedback: ready({ ...practicePageCard, state: "READY", deliveredAt: null }),
	},
	play: async () => {
		const panelElement = await settledDrawerPanel();
		await expectHeaderChip(panelElement, "Ready for their practice pages");
		await expect(
			within(panelElement).getByText(/counts as delivered when they open it/u),
		).toBeVisible();
		await expect(within(panelElement).queryByText(/tried again/u)).toBeNull();
	},
};

/**
 * The longest realistic record, at the narrowest width: a rendered body with code, and every copy's
 * receipt.
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

/**
 * Awaiting approval, the level leads with what approval authorizes and ends in the decision. What
 * will be sent is expanded — the summary and every note — rather than behind a disclosure.
 */
export const AwaitingApproval: Story = {
	args: { feedback: ready(awaitingApprovalFeedbackDetail) },
	play: async ({ args, userEvent }) => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.getByText(/Approval authorizes every copy above/u)).toBeVisible();
		await expect(panel.getByText(/^A summary, a line note,? and a file note$/u)).toBeVisible();
		await expect(
			panel.getByText(/This name says the delay doubles, which is the rule a reader needs/u),
		).toBeVisible();
		panel.getByText("It waits for a workspace admin to approve or reject it.");

		// The decision is the footer, like every sheet's actions, so it stays in reach under the package.
		const approve = panel.getByRole("button", { name: "Approve for delivery" });
		const reject = panel.getByRole("button", { name: "Reject feedback" });
		await expect(approve.closest('[data-slot="drawer-footer"]')).not.toBeNull();
		await expect(reject.closest('[data-slot="drawer-footer"]')).not.toBeNull();
		await userEvent.click(approve);
		await expect(args.onApprove).toHaveBeenCalledOnce();
	},
};

export const AwaitingApprovalReflow: Story = {
	args: { feedback: ready(awaitingApprovalFeedbackDetail) },
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
		feedback: ready(awaitingApprovalFeedbackDetail),
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
		feedback: ready(awaitingApprovalFeedbackDetail),
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
		feedback: ready(awaitingApprovalFeedbackDetail),
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

/** A proposal with no copies has nothing to authorize, so only rejection is offered. */
export const PackageUnavailable: Story = {
	args: { feedback: ready({ ...awaitingApprovalFeedbackDetail, copies: [] }) },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.getByRole("alert")).toHaveTextContent("This review package is unavailable");
		await expectGenuinelyDisabled(panel.getByRole("button", { name: "Approve for delivery" }));
		await expect(panel.getByRole("button", { name: "Reject feedback" })).toBeEnabled();
	},
};

export const RejectingWithContext: Story = {
	args: { feedback: ready(awaitingApprovalFeedbackDetail) },
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
	args: { feedback: ready(awaitingApprovalFeedbackDetail), isDeciding: true },
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
