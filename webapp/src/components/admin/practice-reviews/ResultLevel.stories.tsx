import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, waitFor, within } from "storybook/test";

import type { ReviewResultDetail } from "@/api/types.gen";
import { withPageBehind } from "@/stories/decorators";
import { InLevelStack } from "@/stories/level-stack";
import { expectSettledVisible, settledDrawerPanel } from "@/stories/overlay";
import { expectNoPanelOverflow } from "@/stories/reflow";
import { daysBefore, hoursBefore } from "@/stories/story-clock";
import { expectGenuinelyDisabled } from "@/test/controls";
import { precedes } from "@/test/dom";

import { resultDetail, reviewResultDetail } from "./fixtures";
import { SHEET_SECTIONS } from "./RecordSheet";
import { ResultLevel } from "./ResultLevel";
import { resultLevel } from "./review-levels";

const ready = (result: ReviewResultDetail) => ({ status: "ready" as const, result });

/** Marked incorrect two hours ago, after an earlier correction that was taken back. */
const markedIncorrect: ReviewResultDetail = {
	...reviewResultDetail,
	invalidations: [
		{
			id: "inv-2",
			reason: "The 404 comes from the router, not the cache.",
			invalidatedAt: hoursBefore(2),
			invalidatedBy: "Ada Admin",
			providerCopy: "INLINE_REMAINS",
		},
		{
			id: "inv-1",
			reason: "Looked like a false positive at first.",
			invalidatedAt: daysBefore(3),
			invalidatedBy: "Ada Admin",
			restorationReason: "The diff does conflate the two after all.",
			restoredAt: daysBefore(2),
			providerCopy: "UPDATED",
		},
	],
};

/**
 * One result's sheet, over the list it was opened from or over a trace, in the order every
 * record sheet has: a status line, What it is, State and history (recorded, disputed, marked
 * incorrect, restored, no longer current), the rationale, the problems it located, the questions it
 * left open and the evidence, Technical (closed) and the actions. It links to no other record; its trace tells where it came from and what it led to.
 * Its one decision — mark it incorrect, or restore it — is the footer, behind a popover that will
 * not submit without a reason, because an invalidation without one is not something the developer
 * could read.
 */
const meta = {
	component: ResultLevel,
	parameters: { layout: "fullscreen" },
	decorators: [withPageBehind],
	tags: ["autodocs"],
	args: {
		path: { behind: [{ label: "Practice reviews", depth: 0 }], onClose: fn() },
		result: ready(reviewResultDetail),
		onChangeValidity: fn(async () => undefined),
		isChangingValidity: false,
	},
	argTypes: { path: { control: false } },
	render: (args) => (
		<InLevelStack entry={resultLevel(reviewResultDetail.id)} path={args.path}>
			{(level) => <ResultLevel {...args} {...level} />}
		</InLevelStack>
	),
} satisfies Meta<typeof ResultLevel>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(
			panel.getByRole("heading", {
				name: "A cache miss and a permission failure come back as the same 404",
				level: 2,
			}),
		).toBeVisible();
		// The sheet is the result alone: no story, and no link to another record. The work links
		// out to its provider.
		await expect(
			panel.queryByRole("group", { name: "How this came about" }),
		).not.toBeInTheDocument();
		const links = panel.queryAllByRole("link");
		for (const link of links) {
			await expect(String(link.getAttribute("href"))).not.toMatch(/detail=|\/reviews\//u);
		}
		await expect(
			panel.getAllByRole("heading", { level: 3 }).map((heading) => heading.textContent),
		).toStrictEqual([
			SHEET_SECTIONS.what,
			SHEET_SECTIONS.state,
			SHEET_SECTIONS.details,
			SHEET_SECTIONS.technical,
		]);
		panel.getByRole("heading", { name: "Why this was raised", level: 4 });
		// The outcome carries its problem count. The result's severity sits beside it, and the worst
		// problem carries the same one.
		await expect(panel.getByText("Not met · 3 problems")).toBeVisible();
		await expect(panel.getAllByText("Major")).toHaveLength(2);
		// The evidence is read as passages, never as the stored record it came from.
		await expect(panel.queryByText(/citations/u)).not.toBeInTheDocument();
		// Standing, it offers the one decision there is, and not its reverse.
		await expect(panel.getByRole("button", { name: "Mark as incorrect" })).toBeEnabled();
		await expect(panel.queryByRole("button", { name: "Restore result" })).not.toBeInTheDocument();
	},
};

/** A result from a review someone requested says so among what it is. */
export const ByHand: Story = {
	args: { result: ready({ ...reviewResultDetail, origin: "MANUAL" }) },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText("Origin");
		await expect(panel.getByText("Requested")).toBeVisible();
		panel.getByText("Someone asked for this review.");
	},
};

/**
 * The rationale renders as Markdown, as on the developer's own row: a file or a value the review
 * quotes is code, not text between backticks.
 */
export const MarkdownRationale: Story = {
	args: {
		result: ready({
			...reviewResultDetail,
			evidenceRationale:
				"`CacheService.find` returns 404 for a cache miss and for a caller who may not read the entry. The second case is a `403`.",
		}),
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		const code = panel.getByText("CacheService.find", { selector: "code" });
		await expect(code).toBeVisible();
		// The typography plugin draws a backtick on each side of inline code unless told not to.
		await expect(getComputedStyle(code, "::before").content).toBe("none");
	},
};

/**
 * Each problem in display order, with its own severity, its explanation as Markdown, the passages
 * that place it and its next step. The example the review recorded belongs to the developer's own
 * pages, so the sheet says nothing of it.
 */
export const Problems: Story = {
	play: async () => {
		const panel = within(await settledDrawerPanel());
		// The list's own items, not the passages listed inside each of them.
		const rows = [...panel.getByRole("list", { name: "Problems" }).children].filter(
			(row): row is HTMLElement => row instanceof HTMLElement,
		);
		await expect(rows.map((row) => row.querySelector("p")?.textContent)).toEqual([
			"A missing row and a missing grant both become a 404",
			"The guard’s 403 never reaches this endpoint",
			"The NotFoundException carries no message",
		]);
		const [first, second, third] = rows.map((row) => within(row));
		if (first === undefined || second === undefined || third === undefined) {
			throw new Error("The sheet does not list three problems");
		}
		await expect(first.getByText("Major")).toBeVisible();
		await expect(second.getByText("Minor")).toBeVisible();
		await expect(third.getByText("Trivial")).toBeVisible();
		await expect(first.getByText("findVisible", { selector: "code" })).toBeVisible();
		first.getByText(/ReviewQueryService\.java:116–119$/u);
		await expect(
			first.getByText(
				"Fetch the row first, then check the grant, and answer 403 when the caller may not read it.",
			),
		).toBeVisible();
		// A problem without a next step does not draw an empty one.
		await expect(second.queryByText("Next step")).not.toBeInTheDocument();
		// The result's own passage stands apart from its problems'.
		const evidence = within(panel.getByRole("region", { name: "Evidence" }));
		evidence.getByText("Do we lose the difference between 'gone' and 'not yours' here?");
		await expect(panel.queryByText("Example from this work")).not.toBeInTheDocument();
		await expect(panel.queryByText(/maps every error in one handler/u)).not.toBeInTheDocument();
		await expect(panel.queryByRole("region", { name: "Open questions" })).not.toBeInTheDocument();
	},
};

/**
 * A review that could not decide names what it could not settle, what would settle it and where the
 * question arose. Open questions are not feedback, and there is no problem to list.
 */
export const OpenQuestions: Story = {
	args: { result: ready(resultDetail("22222222-5555-5555-5555-555555555555")) },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.getAllByText("Could not determine")[0]).toBeVisible();
		const questions = within(panel.getByRole("list", { name: "Open questions" }));
		await expect(questions.getAllByRole("listitem")).toHaveLength(2);
		await expect(
			questions.getByText("Was the restore order agreed with the on-call team?"),
		).toBeVisible();
		questions.getByText(
			"Would settle it: A link to the discussion or incident that chose this order.",
		);
		// A document is not a file, so its location is its name, without a line.
		questions.getByText("Runbook: restoring a workspace from backup", { selector: "code" });
		// The second question arose nowhere in particular, so it names no place.
		await expect(questions.getAllByText(/^At/u)).toHaveLength(1);
		await expect(panel.queryByRole("list", { name: "Problems" })).not.toBeInTheDocument();
		await expect(panel.queryByText(/problems?$/u)).not.toBeInTheDocument();
	},
};

/** A not met result with one problem says so in the singular. */
export const OneProblem: Story = {
	args: { result: ready(resultDetail("88888888-8888-8888-8888-888888888888")) },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.getByText("Not met · 1 problem")).toBeVisible();
		await expect(panel.getAllByText("Trivial").length).toBeGreaterThan(0);
		// Its only passage is the problem's, so the sheet draws no empty evidence section of its own.
		await expect(panel.queryByRole("region", { name: "Evidence" })).not.toBeInTheDocument();
	},
};

/** A met result has no problems, so its own passages are the whole of its evidence. */
export const Met: Story = {
	args: { result: ready(resultDetail("55555555-5555-5555-5555-555555555555")) },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.getAllByText("Met")[0]).toBeVisible();
		await expect(panel.queryByRole("list", { name: "Problems" })).not.toBeInTheDocument();
		within(panel.getByRole("region", { name: "Evidence" })).getByText(
			"One passage from one source.",
		);
	},
};

export const Reflow: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async () => {
		await expectNoPanelOverflow(await settledDrawerPanel());
	},
};

/**
 * The developer disputes feedback written from the result. The dispute answers this
 * result, so it is in its history in full, in their words, naming the feedback by where it
 * went without linking it. Their ratings and private notes are theirs and are not shown.
 */
export const Disputed: Story = {
	args: {
		result: ready({
			...reviewResultDetail,
			disputes: [
				{
					feedbackId: "dddddddd-2222-2222-2222-222222222222",
					channel: "IN_APP",
					explanation: "The retry is the caller's job here; this service only reports the failure.",
					disputedAt: hoursBefore(3),
				},
			],
		}),
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getAllByText("Disputed");
		panel.getByText("Disputed by the developer, on feedback on their practice pages");
		panel.getByText("The retry is the caller's job here; this service only reports the failure.");
		await expect(panel.queryByRole("alert")).toBeNull();
		await expect(panel.queryByRole("link", { name: /practice pages/u })).not.toBeInTheDocument();
		panel.getByRole("button", { name: "Mark as incorrect" });
	},
};

/** A result that is no longer current says so in its status line and in its history. */
export const NoLongerCurrent: Story = {
	args: {
		result: ready({
			...resultDetail("bbbbbbbb-2222-2222-2222-222222222222"),
			supersededAt: hoursBefore(5),
		}),
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText("Historical result");
		panel.getByText("No longer current");
		await expect(panel.queryByRole("alert")).toBeNull();
	},
};

/** The server hides the evidence: the sheet says so, and never that nothing was quoted. */
export const EvidenceHidden: Story = {
	args: {
		result: ready({
			...reviewResultDetail,
			evidenceHidden: true,
			evidence: undefined,
			observations: [],
		}),
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText("The evidence of this result is hidden.");
		await expect(panel.queryByText(/Nothing was quoted/u)).toBeNull();
	},
};

/** The reason is required: an invalidation without one is not something the developer could read. */
export const MarkAsIncorrect: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, userEvent }) => {
		const panel = within(await settledDrawerPanel());
		await userEvent.click(panel.getByRole("button", { name: "Mark as incorrect" }));
		const popover = await screen.findByRole("dialog", {
			name: "Mark this result as incorrect",
		});
		const reason = within(popover).getByRole("textbox", { name: "Reason" });
		await expectSettledVisible(reason);
		const submit = within(popover).getByRole("button", { name: "Mark as incorrect" });
		await expectGenuinelyDisabled(submit);
		await userEvent.type(reason, "  The 404 comes from the router, not the cache.  ");
		await userEvent.click(submit);
		await expect(args.onChangeValidity).toHaveBeenCalledWith(
			false,
			"The 404 comes from the router, not the cache.",
		);
		await waitFor(async () => expect(screen.queryByRole("textbox", { name: "Reason" })).toBeNull());
	},
};

/** A refused change keeps the form open with the reason the admin wrote, ready for another try. */
export const MarkAsIncorrectFails: Story = {
	args: {
		onChangeValidity: fn(async () => {
			throw new Error("409");
		}),
	},
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, userEvent }) => {
		const panel = within(await settledDrawerPanel());
		await userEvent.click(panel.getByRole("button", { name: "Mark as incorrect" }));
		const popover = await screen.findByRole("dialog", {
			name: "Mark this result as incorrect",
		});
		const reason = within(popover).getByRole("textbox", { name: "Reason" });
		await expectSettledVisible(reason);
		await userEvent.type(reason, "The 404 comes from the router.");
		await userEvent.click(within(popover).getByRole("button", { name: "Mark as incorrect" }));
		await expect(args.onChangeValidity).toHaveBeenCalledOnce();
		await expect(within(popover).getByRole("textbox", { name: "Reason" })).toHaveValue(
			"The 404 comes from the router.",
		);
	},
};

/**
 * A comment already on the work could not be changed, so the level says so rather than implying the
 * correction reached it. The footer offers the reverse instead — restore — and nothing else.
 */
export const MarkedIncorrect: Story = {
	args: { result: ready(markedIncorrect) },
	play: async () => {
		const panelElement = await settledDrawerPanel();
		const panel = within(panelElement);
		// The header says it under the title, where its standing goes, and only there.
		const [chip, ...others] = panel.getAllByText("Marked incorrect", {
			selector: "[data-sheet-status] *",
		});
		if (!chip) {
			throw new Error("The level does not say it was marked incorrect");
		}
		await expect(others).toHaveLength(0);
		await expect(precedes(panel.getByRole("heading", { level: 2 }), chip)).toBe(true);
		await expect(
			panel.getByText(
				/Comments Hephaestus posted about specific lines, whether on the lines or linking to them, are still on the work unchanged/u,
			),
		).toBeVisible();
		await expect(
			panel.getByText("The correction notice was removed from posted comments."),
		).toBeVisible();
		await expect(panel.getByRole("button", { name: "Restore result" })).toBeEnabled();
		await expect(
			panel.queryByRole("button", { name: "Mark as incorrect" }),
		).not.toBeInTheDocument();
		// Oldest first: recorded, the earlier correction and its restore, then the one in force.
		const history = within(panel.getByRole("list", { name: "History" }));
		await expect(history.getAllByRole("listitem").map((entry) => entry.textContent)).toEqual([
			expect.stringMatching(/^Recorded/u),
			expect.stringMatching(/^Marked incorrect.*by Ada Admin/u),
			expect.stringMatching(/^Restored.*by an account that no longer exists/u),
			expect.stringMatching(/^Marked incorrect.*by Ada Admin/u),
		]);
	},
};

/** Restoring is the same popover the other way round, and reports `true` with its reason. */
export const RestoreAResult: Story = {
	args: { result: ready(markedIncorrect) },
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, userEvent }) => {
		const panel = within(await settledDrawerPanel());
		await userEvent.click(panel.getByRole("button", { name: "Restore result" }));
		const popover = await screen.findByRole("dialog", { name: "Restore this result" });
		await expectSettledVisible(
			within(popover).getByText(/Feedback that was stopped stays stopped, and nothing is re-sent/u),
		);
		const reason = within(popover).getByRole("textbox", { name: "Reason" });
		await userEvent.type(reason, "The router does return the cache's 404.");
		await userEvent.click(within(popover).getByRole("button", { name: "Restore result" }));
		await expect(args.onChangeValidity).toHaveBeenCalledWith(
			true,
			"The router does return the cache's 404.",
		);
	},
};

/** While the server decides, the footer's control shows it is working and takes no second press. */
export const ChangingValidity: Story = {
	args: { isChangingValidity: true },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expectGenuinelyDisabled(panel.getByRole("button", { name: "Mark as incorrect" }));
	},
};

/**
 * The drawer is named by its title, so the heading stands while the record loads; the path stays
 * usable, and there is no decision to offer about a record not yet read.
 */
export const Loading: Story = {
	args: { result: { status: "loading" } },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.getByRole("heading", { level: 2 })).toHaveAccessibleName("Loading result");
		panel.getByRole("button", { name: "Practice reviews" });
		await expect(panel.queryByText("We could not load this result")).not.toBeInTheDocument();
		await expect(
			panel.queryByRole("button", { name: "Mark as incorrect" }),
		).not.toBeInTheDocument();
	},
};

/**
 * The error arrives as a prop, so nothing here depends on a request failing at the right moment. A
 * status-less error is the one that reads "check your connection" — see `QueryErrorAlert`.
 */
export const LoadFailed: Story = {
	args: {
		result: {
			status: "error",
			error: { status: 500 },
			onRetry: fn(),
		},
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.getByText("We could not load this result")).toBeVisible();
		// With no record to name it, the level is named for what it is.
		await expect(screen.getByRole("dialog")).toHaveAccessibleName("Result");
		await expect(
			panel.queryByRole("button", { name: "Mark as incorrect" }),
		).not.toBeInTheDocument();
		await expect(panel.getByRole("button", { name: "Retry" })).toBeEnabled();
	},
};
