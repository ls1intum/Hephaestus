import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent } from "storybook/test";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/test/reflow";
import { ReflectionPage } from "./ReflectionPage";
import { oneReflectionFeedback, reflectionFeedback } from "./story-mock-data";

/**
 * The developer's own reflection surface — the third feedback lane, and the only one that is a screen.
 *
 * Feedback on the work says what is wrong in one change; the mentor conversation asks the developer
 * to judge their own work; this page names what recurs across several pieces of their work and what
 * to do differently next time. Every story below is a prop, because the route does the asking and
 * this screen only shows the answer.
 *
 * Two things the stories assert rather than describe: that a figure never appears on its own — a
 * count on this page would be read as a score — and that each pattern carries both its evidence and
 * a next step, which is the one rule that survives all three lanes.
 */
const meta = {
	title: "My feedback/Whole page",
	component: ReflectionPage,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [320, 768, 1440] },
	},
	decorators: [withWidePage, withStandardPage],
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		feedback: reflectionFeedback,
		isLoading: false,
		error: undefined,
		onRetry: fn(),
	},
} satisfies Meta<typeof ReflectionPage>;

export default meta;
type Story = StoryObj<typeof meta>;

const FIRST_EVIDENCE_LINK =
	"Pull or merge request: New tax-exempt branch ships without a test" as const;

export const SeveralPatterns: Story = {
	play: async ({ canvas }) => {
		await expect(
			await canvas.findByRole("heading", { level: 1, name: "My feedback" }),
		).toBeVisible();
		await expect(
			canvas.getByRole("heading", { level: 2, name: "What recurs in your work" }),
		).toBeVisible();

		// Written out rather than read back off the fixture, so a fixture that loses a pattern fails
		// here instead of quietly agreeing with the page.
		await expect(
			canvas.getByRole("heading", { level: 3, name: "Tests are arriving one commit late" }),
		).toBeVisible();
		await expect(
			canvas.getByRole("heading", {
				level: 3,
				name: "Changes are growing past the point where one sitting can review them",
			}),
		).toBeVisible();
		await expect(
			canvas.getByRole("heading", {
				level: 3,
				name: "Descriptions say what changed, not why it changed",
			}),
		).toBeVisible();

		// Evidence and a next step, on every one of them. This is the invariant the lane exists under,
		// so it is counted rather than sampled.
		await expect(canvas.getAllByText("Try next:")).toHaveLength(3);
		await expect(canvas.getByText("Seen on 3 pieces of your work")).toBeVisible();
		await expect(canvas.getAllByText("Seen on 2 pieces of your work")).toHaveLength(2);

		// No figure stands alone anywhere on the page: `occurrenceCount` is 3 on the first pattern and
		// is never printed, because a number by itself on this surface reads as a score.
		await expect(canvas.queryByText("3")).not.toBeInTheDocument();
		await expect(canvas.queryByText("2")).not.toBeInTheDocument();

		// The practice is named as a practice, and the catalog's learner framing travels with it. The
		// criteria the detector was given never do.
		await expect(canvas.getAllByText("Why this matters")).toHaveLength(3);
		await expect(canvas.getAllByText("What good looks like")).toHaveLength(3);
	},
};

/**
 * The kind of work is on an icon, so it is also in the link's accessible name: an icon carries no
 * meaning to a screen reader and none at all without colour (WCAG 1.4.1). The row whose observation
 * recorded no title falls back to the kind alone rather than printing the artifact id, which is a
 * database key and not a number anyone has seen on a pull request.
 */
export const EvidenceNamesTheKindOfWork: Story = {
	play: async ({ canvas }) => {
		await expect(await canvas.findByRole("link", { name: FIRST_EVIDENCE_LINK })).toBeVisible();
		await expect(
			canvas.getByRole("link", {
				name: "Pull or merge request: Retry backoff added without a test for the second attempt",
			}),
		).toBeVisible();
		await expect(
			canvas.getByRole("link", { name: "Issue: Issue restates the title in the body" }),
		).toBeVisible();
		// The untitled occurrence: named by its kind, and by its kind exactly once.
		await expect(canvas.getByRole("link", { name: "Pull or merge request" })).toBeVisible();
	},
};

/** Everything above the first piece of evidence is reachable by keyboard, in reading order. */
export const EveryPieceOfEvidenceIsReachable: Story = {
	play: async ({ canvas }) => {
		const target = await canvas.findByRole("link", { name: FIRST_EVIDENCE_LINK });
		for (let press = 0; press < 12 && document.activeElement !== target; press += 1) {
			await userEvent.tab();
		}
		await expect(target).toHaveFocus();
	},
};

export const OnePattern: Story = {
	args: { feedback: oneReflectionFeedback },
	play: async ({ canvas }) => {
		await expect(
			await canvas.findByRole("heading", { level: 3, name: "Tests are arriving one commit late" }),
		).toBeVisible();
		await expect(canvas.getAllByRole("article")).toHaveLength(1);
		await expect(canvas.getByText("Seen on 3 pieces of your work")).toBeVisible();
	},
};

/**
 * The common case for anyone new, and the state this screen is most likely to be judged on.
 *
 * It is written as a fact about the work rather than about the reader: what has to be true before
 * anything appears, and what an empty page does not mean. No praise, because praise aimed at the
 * person is the least useful feedback there is and this whole surface exists to avoid it — and no
 * apology, because nothing has gone wrong.
 */
export const NothingPreparedYet: Story = {
	args: { feedback: [] },
	play: async ({ canvas }) => {
		await expect(await canvas.findByText("No feedback prepared for you yet")).toBeVisible();
		await expect(
			canvas.getByText(/there is nothing recurring to say, not that anything went wrong/),
		).toBeVisible();

		// The way out points at the member-facing review activity, never at an operator surface: the
		// text on this page is withheld from those on purpose, and a link would imply otherwise.
		const link = canvas.getByRole("link", {
			name: "See what has been reviewed in this workspace",
		});
		await expect(link).toBeVisible();
		await expect(link.getAttribute("href")).not.toMatch(/\/admin/);
		await expect(canvas.queryByRole("link", { name: /admin/i })).not.toBeInTheDocument();
	},
};

/** Two blocks, not the page cap: the list is short by design, so a full-page skeleton would lie. */
export const Loading: Story = {
	args: { feedback: undefined, isLoading: true },
	play: async ({ canvas }) => {
		const status = (await canvas.findByText("Loading your feedback")).closest('[role="status"]');
		if (!(status instanceof HTMLElement)) throw new Error("The skeleton is not a live region");
		await expect(status.querySelectorAll(":scope > div")).toHaveLength(2);
		await expect(canvas.queryByText("No feedback prepared for you yet")).not.toBeInTheDocument();
	},
};

export const LoadFailed: Story = {
	args: {
		feedback: undefined,
		error: { status: 500, title: "Internal Server Error", detail: "The composer is unreachable." },
	},
	play: async ({ args, canvas }) => {
		await expect(await canvas.findByText("Couldn't load your feedback")).toBeVisible();
		await expect(canvas.getByText(/The composer is unreachable/)).toBeVisible();
		// A failure is not an empty page: a developer must never read "nothing prepared for you" when
		// the truth is that the answer never arrived.
		await expect(canvas.queryByText("No feedback prepared for you yet")).not.toBeInTheDocument();

		await userEvent.click(canvas.getByRole("button", { name: "Retry" }));
		await expect(args.onRetry).toHaveBeenCalledTimes(1);
	},
};

/** Not a member of this workspace any more. Retrying would only fail again, so it is withheld. */
export const LoadRefused: Story = {
	args: {
		feedback: undefined,
		error: { status: 403, title: "Forbidden", detail: "You are not a member of this workspace." },
	},
	play: async ({ args, canvas }) => {
		await expect(await canvas.findByText("Couldn't load your feedback")).toBeVisible();
		await expect(canvas.queryByRole("button", { name: "Retry" })).not.toBeInTheDocument();
		await expect(args.onRetry).not.toHaveBeenCalled();
	},
};

export const Mobile: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvas }) => {
		await expect(
			await canvas.findByRole("heading", { level: 3, name: "Tests are arriving one commit late" }),
		).toBeVisible();
		await expectNoPageOverflow();
	},
};
