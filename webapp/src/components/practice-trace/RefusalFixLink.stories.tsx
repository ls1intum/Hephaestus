import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { RefusalFixLink, type RefusalFixLinkProps } from "./RefusalFixLink";
import { REFUSAL_FIXES, SIGNAL_STATE_REASON_LABELS, type SignalStateReason } from "./trace-format";

const REASONS = Object.keys(SIGNAL_STATE_REASON_LABELS) as SignalStateReason[];

/**
 * Where each fix lands, written out rather than recomputed.
 *
 * Deriving the expectation from `REFUSAL_FIXES` — branching on `section` the way the component
 * does — makes a wrong component and a wrong test agree. These are the eight URLs a reader should
 * be able to check against the router by eye. `how-much` is the Review page's default section and
 * so carries no search param; the other two sections do.
 */
const EXPECTED_HREFS: Partial<Record<SignalStateReason, string>> = {
	GATE_SKIPPED: "/w/demo/admin/practices/review?section=when-and-where",
	OUT_OF_REVIEW_SCOPE: "/w/demo/admin/practices/review?section=when-and-where",
	PRACTICES_DISABLED: "/w/demo/admin/practices/review?section=when-and-where",
	PRACTICE_TIER_OFF: "/w/demo/admin/practices/review",
	NO_ACTIVE_PRACTICE: "/w/demo/admin/practices",
	REVIEW_MODEL_UNBOUND: "/w/demo/admin/models",
	MODEL_UNAVAILABLE: "/w/demo/admin/models",
	BUDGET_EXHAUSTED: "/w/demo/admin/usage",
};

/**
 * The whole refusal vocabulary at once, each reason beside the fix it does or does not offer.
 *
 * Rendered as one list rather than a story per reason because the interesting property is the
 * *shape of the table*: which refusals hand over a destination and which deliberately do not. A
 * reader checking whether a new reason was wired up is comparing it against its neighbours.
 *
 * Takes the component's own props and overrides only `reason`, so the Controls panel still drives
 * every other input on the stories that render this overview.
 */
function RefusalCatalogue(props: RefusalFixLinkProps) {
	return (
		<ul className="max-w-2xl space-y-2 text-sm">
			{REASONS.map((reason) => (
				<li key={reason} className="flex flex-wrap items-baseline gap-x-1.5">
					<span className="text-muted-foreground">{SIGNAL_STATE_REASON_LABELS[reason]}.</span>
					<RefusalFixLink {...props} reason={reason} />
				</li>
			))}
		</ul>
	);
}

const meta = {
	title: "Practice trace/Refusal fix link",
	component: RefusalFixLink,
	parameters: { layout: "padded", chromatic: { viewports: [320, 1440] } },
	tags: ["autodocs"],
	args: { workspaceSlug: "demo", reason: "REVIEW_MODEL_UNBOUND", canAdminister: true },
} satisfies Meta<typeof RefusalFixLink>;

export default meta;
type Story = StoryObj<typeof meta>;

/** One refusal, one door: the shape every other story here is a survey of. */
export const Default: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("link", { name: "Set up a review model" })).toHaveAttribute(
			"href",
			"/w/demo/admin/models",
		);
	},
};

/**
 * The other of the two shapes a fix has: a section of the Review page, which is one route plus a
 * search param rather than a route of its own.
 */
export const ASectionOfTheReviewPage: Story = {
	args: { reason: "GATE_SKIPPED" },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("link", { name: "Open Review: When and where" })).toHaveAttribute(
			"href",
			"/w/demo/admin/practices/review?section=when-and-where",
		);
	},
};

/** A cooldown expires on its own, so there is nothing to send an admin to — and nothing renders. */
export const NoFixForThisReason: Story = {
	args: { reason: "COOLDOWN_ACTIVE" },
	play: async ({ canvasElement }) => {
		await expect(within(canvasElement).queryByRole("link")).toBeNull();
	},
};

/**
 * Every reason an admin can act on, and every one they cannot.
 *
 * The gaps are as deliberate as the links. A cooldown expires, an allowance refills, a duplicate
 * is already running, deleted work stays deleted — none of those has a setting behind it, and a link
 * offered anyway would teach an admin to change something to make a non-fault stop.
 */
export const EveryReason: Story = {
	render: (args) => <RefusalCatalogue {...args} />,
	play: async ({ canvas }) => {
		const links = canvas.getAllByRole("link");
		await expect(links).toHaveLength(Object.keys(EXPECTED_HREFS).length);
		// Each accessible name names where it goes: a link is read out of its sentence, so "here" or
		// "fix this" identifies nothing (WCAG 2.4.4).
		for (const link of links) {
			await expect(link).toHaveAccessibleName(/^(Open|Set up) \S/);
		}
	},
};

/**
 * The reasons a workspace admin can undo, one destination each.
 *
 * Three of them share the Review page's *When and where* section and two share AI models — the
 * same screen reached by different sentences, which is why a label names the section it lands on
 * rather than repeating the reason it came from.
 */
export const WhereEachFixLives: Story = {
	render: (args) => <RefusalCatalogue {...args} />,
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas }) => {
		// A reason that grows a fix without an entry above fails here rather than going unchecked.
		await expect(Object.keys(EXPECTED_HREFS).sort()).toEqual(Object.keys(REFUSAL_FIXES).sort());

		for (const [reason, href] of Object.entries(EXPECTED_HREFS)) {
			const sentence = SIGNAL_STATE_REASON_LABELS[reason as SignalStateReason];
			const row = canvas.getByText(`${sentence}.`).closest("li");
			if (!(row instanceof HTMLElement)) throw new Error(`No row for ${reason}`);
			await expect(within(row).getByRole("link")).toHaveAttribute("href", href);
		}
	},
};

export const AMemberSeesNoLinks: Story = {
	args: { canAdminister: false },
	render: (args) => <RefusalCatalogue {...args} />,
	play: async ({ canvas }) => {
		await expect(canvas.getByText("No AI model is set up to run reviews.")).toBeVisible();
		await expect(canvas.queryAllByRole("link")).toHaveLength(0);
	},
};
