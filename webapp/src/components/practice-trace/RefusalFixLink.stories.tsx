import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { RefusalFixLink } from "./RefusalFixLink";
import { REFUSAL_FIXES, SIGNAL_STATE_REASON_LABELS, type SignalStateReason } from "./trace-format";

const REASONS = Object.keys(SIGNAL_STATE_REASON_LABELS) as SignalStateReason[];

/**
 * The whole refusal vocabulary at once, each reason beside the fix it does or does not offer.
 *
 * <p>Rendered as one list rather than a story per reason because the interesting property is the
 * *shape of the table*: which refusals hand over a destination and which deliberately do not. A
 * reader checking whether a new reason was wired up is comparing it against its neighbours.
 */
function RefusalCatalogue({ canAdminister }: { canAdminister: boolean }) {
	return (
		<ul className="max-w-2xl space-y-2 text-sm">
			{REASONS.map((reason) => (
				<li key={reason} className="flex flex-wrap items-baseline gap-x-1.5">
					<span className="text-muted-foreground">{SIGNAL_STATE_REASON_LABELS[reason]}.</span>
					<RefusalFixLink workspaceSlug="demo" reason={reason} canAdminister={canAdminister} />
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

/**
 * Every reason an admin can act on, and every one they cannot.
 *
 * <p>The gaps are as deliberate as the links. A cooldown expires, an allowance refills, a duplicate
 * is already running, deleted work stays deleted — none of those has a setting behind it, and a link
 * offered anyway would teach an admin to change something to make a non-fault stop.
 */
export const EveryReason: Story = {
	render: () => <RefusalCatalogue canAdminister />,
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const links = canvas.getAllByRole("link");
		await expect(links).toHaveLength(Object.keys(REFUSAL_FIXES).length);
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
 * <p>Three of them share the Review page's *When and where* section and two share AI models — the
 * same screen reached by different sentences, which is why a label names the section it lands on
 * rather than repeating the reason it came from.
 */
export const WhereEachFixLives: Story = {
	render: () => <RefusalCatalogue canAdminister />,
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		for (const [reason, fix] of Object.entries(REFUSAL_FIXES)) {
			const sentence = SIGNAL_STATE_REASON_LABELS[reason as SignalStateReason];
			const row = canvas.getByText(`${sentence}.`).closest("li");
			if (!(row instanceof HTMLElement)) throw new Error(`No row for ${reason}`);
			const expected = fix.section
				? `/w/demo/admin/practices/review${fix.section === "how-much" ? "" : `?section=${fix.section}`}`
				: fix.to.replace("$workspaceSlug", "demo");
			await expect(within(row).getByRole("link", { name: fix.label })).toHaveAttribute(
				"href",
				expected,
			);
		}
	},
};

/** A member sees the same sixteen sentences and not one door they cannot open. */
export const AMemberSeesNoLinks: Story = {
	render: () => <RefusalCatalogue canAdminister={false} />,
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("No AI model is set up to run reviews.")).toBeVisible();
		await expect(canvas.queryAllByRole("link")).toHaveLength(0);
	},
};
