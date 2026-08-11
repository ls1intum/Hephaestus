import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, waitFor, within } from "storybook/test";
import { withWidePage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/test/reflow";
import { ReviewAutonomyPage } from "./ReviewAutonomyPage";
import { type AutonomyFixture, buildAutonomyFixture, scaleFixture } from "./story-mock-data";

/**
 * A fixture supplies three of the page's args and nothing else; the rest come from the meta. Named
 * here rather than inline so the fixtures read as a set and a story can be about the one field it
 * changes.
 */
const from = ({ settings, rollup, practices }: AutonomyFixture) => ({
	settings,
	rollup,
	practices,
});

const small = buildAutonomyFixture({
	areas: [
		{
			slug: "pull-request-hygiene",
			name: "Pull request hygiene",
			practices: [
				{
					name: "States the motivation",
					whyItMatters:
						"Your reviewer was not in your head when you wrote the code. A sentence on why you made the change spares them from reverse-engineering your intent from the diff.",
				},
				{ name: "Links the issue it closes", override: "PROPOSE" },
			],
		},
		{
			slug: "testing",
			name: "Testing",
			override: "OFF",
			practices: [{ name: "Covers the new branch" }, { name: "Names what it asserts" }],
		},
		{
			slug: null,
			name: null,
			practices: [{ name: "Handles the error state" }],
		},
	],
});

const chosenByTheWorkspace = buildAutonomyFixture({
	workspaceDefault: "PROPOSE",
	feedbackReach: "CONVERSATION",
	areas: [
		{
			slug: "pull-request-hygiene",
			name: "Pull request hygiene",
			practices: [{ name: "States the motivation" }, { name: "Links the issue it closes" }],
		},
	],
});

const oneDescribedAndOneBare = buildAutonomyFixture({
	areas: [
		{
			slug: "documentation",
			name: "Documentation",
			practices: [
				{
					name: "Explains the trade-off it chose",
					artifactKind: "docs.document",
					whyItMatters:
						"A decision without its alternatives reads as arbitrary six months later. Naming what you did not do is what lets the next person tell a considered choice from an accident.",
				},
				{ name: "Written by hand, and says nothing more" },
			],
		},
	],
});

const oneDescribedPractice = buildAutonomyFixture({
	areas: [
		{
			slug: "documentation",
			name: "Documentation",
			practices: [
				{
					name: "Explains the trade-off it chose",
					artifactKind: "docs.document",
					whyItMatters: "A decision without its alternatives reads as arbitrary six months later.",
				},
			],
		},
	],
});

const nothingSetByHand = buildAutonomyFixture({
	areas: [{ slug: "testing", name: "Testing", practices: [{ name: "Covers the new branch" }] }],
});

const oneUnreviewablePractice = buildAutonomyFixture({
	areas: [
		{
			slug: "observability",
			name: "Observability",
			practices: [{ name: "Alerts a human on failure", reviewable: false }],
		},
	],
});

/** Built once: three stories render the same hundred rows, and building it is not free. */
const atScale = scaleFixture();

const idle = {
	workspace: false,
	areaSlugs: new Set<string>(),
	practiceSlugs: new Set<string>(),
	bulk: null,
};

const meta = {
	title: "Workspace admin/Practices/Review/How much",
	component: ReviewAutonomyPage,
	parameters: {
		layout: "padded",
		chromatic: { viewports: [320, 1440] },
		viewport: { defaultViewport: "reflow" },
	},
	args: {
		workspaceSlug: "demo",
		settings: small.settings,
		rollup: small.rollup,
		practices: small.practices,
		pending: idle,
		overridesOnly: false,
		onOverridesOnlyChange: fn(),
		onSetWorkspaceDefault: fn(),
		onClearWorkspaceDefault: fn(),
		onSetFeedbackReach: fn(),
		onClearFeedbackReach: fn(),
		onSetAreaTier: fn(),
		onClearAreaTier: fn(),
		onSetPracticeTier: fn(),
		onClearPracticeTier: fn(),
		onBulkSetTier: fn(),
	},
	decorators: [withWidePage],
	tags: ["autodocs"],
} satisfies Meta<typeof ReviewAutonomyPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * A workspace that has never expressed an opinion. The default reads Deliver because that is what an
 * unset chain resolves to, and the line under it says so rather than pretending somebody chose it.
 */
export const WorkspaceDefaultUnset: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Not chosen yet, so Deliver applies.")).toBeVisible();
		await expect(
			canvas.getByRole("radiogroup", { name: "How far Hephaestus may go without you" }),
		).toBeVisible();
		await expect(canvas.queryByText(/How loud/i)).not.toBeInTheDocument();
		await expectNoPageOverflow();
	},
};

export const WorkspaceDefaultSet: Story = {
	args: from(chosenByTheWorkspace),
	play: async ({ args, canvas, userEvent }) => {
		await expect(
			within(
				canvas.getByRole("radiogroup", { name: "How far Hephaestus may go without you" }),
			).getByRole("radio", { name: "Propose" }),
		).toBeChecked();
		await expect(
			within(canvas.getByRole("radiogroup", { name: "Where feedback may go" })).getByRole("radio", {
				name: /In the mentor conversation/,
			}),
		).toBeChecked();

		const resets = canvas.getAllByRole("button", { name: /^Use the default/ });
		await userEvent.click(resets[0]);
		await expect(args.onClearWorkspaceDefault).toHaveBeenCalled();
	},
};

/**
 * An area that decided for itself. Its rung is not muted, its heading counts the practices it now
 * governs, and the summary above still answers for the whole workspace.
 */
export const AreaOverride: Story = {
	play: async ({ args, canvas, userEvent }) => {
		const testing = canvas.getByRole("radiogroup", {
			name: "How far Hephaestus may go in Testing",
		});
		await expect(within(testing).getByRole("radio", { name: "Off" })).toBeChecked();
		await userEvent.click(within(testing).getByRole("radio", { name: "Deliver" }));
		await expect(args.onSetAreaTier).toHaveBeenCalledWith("testing", "DELIVER");

		// The bucket for practices in no area holds no tier of its own — it is not a row anywhere.
		const unassigned = canvas.getByText("Not in an area").closest('[data-slot="accordion-item"]');
		if (!(unassigned instanceof HTMLElement)) throw new Error("No-area group not rendered");
		await expect(within(unassigned).getByText("Follows the workspace default")).toBeVisible();
		await expect(
			within(unassigned).queryByRole("radiogroup", { name: /How far Hephaestus may go in/ }),
		).not.toBeInTheDocument();
	},
};

export const PracticeOverrideWithReset: Story = {
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: /Pull request hygiene/ }));
		const row = canvas.getByText("Links the issue it closes").closest("li");
		if (!(row instanceof HTMLElement)) throw new Error("Practice row not rendered");

		// The status and its reset are one line now. `getByText` matches an element's own text nodes, so
		// this is the paragraph — the button inside it is asserted by role below, which is the pair.
		await expect(within(row).getByText("Set here.")).toBeVisible();
		await expect(
			within(row).getByRole("radiogroup", {
				name: "How far Hephaestus may go on Links the issue it closes",
			}),
		).toBeVisible();

		const sibling = canvas.getByText("States the motivation").closest("li");
		if (!(sibling instanceof HTMLElement)) throw new Error("Sibling row not rendered");
		await expect(within(sibling).getByText(/^Follows/)).toBeVisible();

		await userEvent.click(
			within(row).getByRole("button", { name: /Use the default for Links the issue it closes/ }),
		);
		await expect(args.onClearPracticeTier).toHaveBeenCalledWith(
			"pull-request-hygiene-links-the-issue-it-closes",
		);
	},
};

/**
 * What a row says about the practice it is deciding for, and what it keeps one gesture away.
 *
 * A name and a control cannot be acted on: "Explains the trade-off" tells an admin nothing about what
 * Hephaestus would say to their team, or how often. The kind of work stays on the row, because it is what
 * makes Deliver cheap or expensive and it has to be readable without a pointer. The catalogue's sentence
 * on why the practice exists moves to a preview card on the name: it is worth reading, it is not what the
 * row is deciding, and under a hundred rows it was what made the list unscannable.
 *
 * The card is optional in both directions — it opens on hover and on focus, and a practice carrying no
 * prose does not get one at all, because an empty popup appearing under the pointer is worse than none.
 * A locally written practice usually carries none, which is the second row here.
 */
export const PracticeContext: Story = {
	args: from(oneDescribedAndOneBare),
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: /Documentation/ }));

		const described = canvas.getByText("Explains the trade-off it chose").closest("li");
		if (!(described instanceof HTMLElement)) throw new Error("Practice row not rendered");
		// The kind of work is named in the vocabulary the practice catalogue uses, not the raw id.
		await expect(within(described).getByText("Document")).toBeVisible();
		// The prose is off the row, so a hundred of these stay scannable.
		await expect(
			within(described).queryByText(/reads as arbitrary six months later/),
		).not.toBeInTheDocument();

		// Hover the name — the card is portalled, so it is found on the screen and not in the row.
		await userEvent.hover(
			within(described).getByRole("link", { name: "Explains the trade-off it chose" }),
		);
		// Re-queried on each poll rather than found once and asserted: the popup mounts at `opacity: 0`
		// and fades in, so a single `findByText` resolves on an element that is not yet visible.
		await waitFor(() =>
			expect(screen.getByText(/reads as arbitrary six months later/)).toBeVisible(),
		);

		// A practice carrying no prose still reads as a row — and gets no card, because there is nothing
		// to put in one. The kind of work is always known; the sentence is not.
		const bare = canvas.getByText("Written by hand, and says nothing more").closest("li");
		if (!(bare instanceof HTMLElement)) throw new Error("Bare row not rendered");
		await expect(within(bare).getByText("Pull or merge request")).toBeVisible();
		// Asserted on the element rather than by hovering and waiting for nothing, which would pass just
		// as well if the card were merely slow.
		await expect(
			within(bare).getByRole("link", { name: "Written by hand, and says nothing more" }),
		).not.toHaveAttribute("data-slot", "hover-card-trigger");
		await expectNoPageOverflow();
	},
};

/**
 * The card is reachable without a mouse.
 *
 * The reason this is a preview card and not a tooltip on a help icon: Base UI's opens on
 * focus-visible as well as on hover, so the tab stop the row already has — the practice's own link — is
 * the keyboard path, and no row grows a second one. Radix's hover card does not do this, which is why
 * its documentation says a hover card may not carry content that matters; this assertion is what keeps
 * that difference from being a claim in a comment.
 *
 * Touch has neither hover nor a focus ring, so the card never opens there. That is why it hangs off
 * the link: the tap goes to the practice, where the same sentence is a field on the form.
 */
export const PracticeDetailOnKeyboardFocus: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	args: from(oneDescribedPractice),
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: /Documentation/ }));
		const link = canvas.getByRole("link", { name: "Explains the trade-off it chose" });

		await expect(screen.queryByText(/reads as arbitrary six months later/)).not.toBeInTheDocument();
		// `link.focus()` would not do: the card opens on focus-*visible*, so focus has to arrive by
		// keyboard. Bounded, so a DOM change ahead of the link fails the story instead of hanging it.
		for (let step = 0; step < 12 && document.activeElement !== link; step++) {
			await userEvent.tab();
		}
		await expect(link).toHaveFocus();
		await waitFor(() =>
			expect(screen.getByText(/reads as arbitrary six months later/)).toBeVisible(),
		);
	},
};

/**
 * The highest-value control on a hundred-row page: what is left is the handful somebody changed —
 * including an area whose own tier was set even though none of its practices were.
 */
export const OverridesOnly: Story = {
	args: { overridesOnly: true },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Links the issue it closes")).toBeVisible();
		await expect(canvas.queryByText("States the motivation")).not.toBeInTheDocument();
		// Testing set its own tier, so it stays — with a line saying why its rows are not listed.
		await expect(canvas.getByRole("button", { name: /^Testing/ })).toBeVisible();
		await expect(canvas.getByText("No practices here were set by hand.")).toBeVisible();
		await expect(canvas.queryByText("Handles the error state")).not.toBeInTheDocument();
	},
};

export const OverridesOnlyEmpty: Story = {
	args: { overridesOnly: true, ...from(nothingSetByHand) },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Nothing was set by hand")).toBeVisible();
	},
};

export const BulkSet: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: /Pull request hygiene/ }));
		await userEvent.click(
			canvas.getByRole("button", { name: /Select all 2 practices in Pull request hygiene/ }),
		);
		await expect(canvas.getByText("2 practices selected")).toBeVisible();

		await userEvent.click(canvas.getByRole("button", { name: "Change the selected" }));
		const menu = within(await screen.findByRole("menu"));
		await userEvent.click(await menu.findByRole("menuitem", { name: "Propose" }));
		await expect(args.onBulkSetTier).toHaveBeenCalledWith(
			[
				"pull-request-hygiene-states-the-motivation",
				"pull-request-hygiene-links-the-issue-it-closes",
			],
			"PROPOSE",
		);
	},
};

export const BulkClearToInherited: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: /Pull request hygiene/ }));
		await userEvent.click(
			canvas.getByRole("checkbox", { name: "Select Links the issue it closes" }),
		);
		await expect(canvas.getByText("1 practice selected")).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: "Change the selected" }));
		const menu = within(await screen.findByRole("menu"));
		await userEvent.click(await menu.findByRole("menuitem", { name: "Use the inherited setting" }));
		await expect(args.onBulkSetTier).toHaveBeenCalledWith(
			["pull-request-hygiene-links-the-issue-it-closes"],
			null,
		);
	},
};

export const BulkInFlight: Story = {
	args: { pending: { ...idle, bulk: { done: 7, total: 24 } } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Changing 7 of 24…")).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Change the selected" })).toBeDisabled();
	},
};

/**
 * A hundred practices across twenty-five areas — the size the old one-row-at-a-time screen could not
 * be used at, and the case nobody tests.
 *
 * The assertions here are about scale rather than about any one row: the summary answers without
 * scrolling and comes from the rollup rather than from counting the rows on screen, and a shut area
 * renders none of its practices, so the page carries twenty-five tier controls rather than a hundred
 * and twenty-five.
 */
export const AtScale: Story = {
	args: from(atScale),
	play: async ({ canvas }) => {
		// The summary is one visible sentence now rather than a middot-separated line with a second,
		// `sr-only` copy of itself spoken beside it. This assertion used to pass against the hidden copy;
		// it now pins the line an admin actually reads, including the count of exceptions.
		await expect(
			canvas.getByText(
				/^100 practices: 6 off, 89 propose and 5 deliver\. \d+ practices and \d+ areas set by hand\.$/,
			),
		).toBeVisible();
		await expect(canvas.getAllByRole("radiogroup")).toHaveLength(27);
		await expect(canvas.queryByRole("checkbox", { name: /^Select / })).not.toBeInTheDocument();
		await expectNoPageOverflow();
	},
};

/**
 * Every decision on the page sits in one column.
 *
 * This is a layout assertion because the bug was invisible to every other kind. An area's ladder used
 * to be laid out after a content-width accordion header, so its left edge moved with the length of the
 * area's name — 285px under "Documentation", 416px under "Pull request hygiene" — and the practice rows
 * below pinned theirs to the right edge instead. Twenty-five areas meant twenty-five left edges. Nothing
 * about the DOM, the roles or the text changed when that happened, so nothing caught it.
 */
export const DecisionsShareOneColumn: Story = {
	args: from(atScale),
	globals: { viewport: { value: "desktop" } },
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas, userEvent }) => {
		// The two-track layout only exists from `sm` up; below it every ladder is full width and shares a
		// left edge whatever the bug is doing. Asserted, so a runner that ignored the viewport would fail
		// here rather than pass the story for the wrong reason.
		await expect(window.innerWidth).toBeGreaterThanOrEqual(640);

		await userEvent.click(canvas.getByRole("button", { name: /^Pull request hygiene/ }));

		const lefts = canvas
			.getAllByRole("radiogroup", { name: /^How far Hephaestus may go (in|on) / })
			.map((group) => Math.round(group.getBoundingClientRect().left));

		await expect(lefts.length).toBeGreaterThan(25);
		// One column, to within the sub-pixel rounding of a grid track inside a grid track.
		await expect(Math.max(...lefts) - Math.min(...lefts)).toBeLessThanOrEqual(2);
	},
};

export const AtScaleOverridesOnly: Story = {
	args: { overridesOnly: true, ...from(atScale) },
	play: async ({ canvas }) => {
		// Two areas set their own tier, three practices did (one of them because Hephaestus cannot
		// review it). Out of a hundred rows, that is the list.
		await expect(canvas.getAllByRole("checkbox", { name: /^Select / })).toHaveLength(3);
		await expect(canvas.getByText(/^Observability: keeps the change/)).toBeVisible();
		await expectNoPageOverflow();
	},
};

/** A practice Hephaestus cannot review is pinned to Off, and says why rather than failing on click. */
export const NotReviewable: Story = {
	args: from(oneUnreviewablePractice),
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: /Observability/ }));
		await expect(
			canvas.getByText("Hephaestus can't review this practice, so it stays off."),
		).toBeVisible();
		await expect(canvas.getByRole("checkbox", { name: /^Select / })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
	},
};
