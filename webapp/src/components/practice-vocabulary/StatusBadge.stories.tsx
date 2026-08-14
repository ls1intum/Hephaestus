import type { Meta, StoryObj } from "@storybook/react-vite";
import { ASSESSMENT_DEFS } from "./assessment-defs";
import { DELIVERY_STATE_DEFS } from "./delivery-outcome-defs";
import { DELIVERY_PLACE_DEFS } from "./delivery-place-defs";
import { PRESENCE_DEFS } from "./presence-defs";
import { REVIEW_STATUS_DEFS, SUMMARY_POST_DEFS } from "./review-status-defs";
import { StatusBadge } from "./StatusBadge";
import { SEVERITY_DEFS } from "./severity-defs";
import { type StatusDef, type StatusDefs, statusValues } from "./status-def";
import { WITHHOLDING_FAMILY_DEFS } from "./withholding-defs";

/**
 * Every status on the practice-review screens renders through this one badge, reading an entry from
 * the registry for its enum.
 *
 * Nothing else may hold words, a colour or an icon for an enum value: the filter dropdown, the table
 * cell, the detail header and the empty state all read the same entry, which is what stops the
 * dropdown showing plain grey text beside a table of coloured tags.
 *
 * Two rules the entries themselves keep, visible in the galleries below. Colour is never the only
 * channel — the variants collapse (two severities are `destructive`, several statuses are `outline`)
 * and the icon is what still separates those under greyscale or colour-vision deficiency. And within
 * one enum no two entries share an icon, because two values that look identical are one value.
 */
const meta = {
	title: "Shared/Practice vocabulary/Status badge",
	component: StatusBadge,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: { def: DELIVERY_STATE_DEFS.DELIVERED },
} satisfies Meta<typeof StatusBadge>;

export default meta;
type Story = StoryObj<typeof meta>;

/** One entry, driven by the Controls panel. */
export const Default: Story = {};

function Gallery<TValue extends string>({
	heading,
	defs,
}: {
	heading: string;
	defs: StatusDefs<TValue>;
}) {
	return (
		<section className="space-y-2">
			<h3 className="text-sm font-semibold">{heading}</h3>
			<dl className="space-y-1.5">
				{statusValues(defs).map((value) => (
					<div key={value} className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
						<dt className="shrink-0">
							<StatusBadge def={defs[value]} />
						</dt>
						<dd className="text-sm text-muted-foreground">{defs[value].description}</dd>
					</div>
				))}
			</dl>
		</section>
	);
}

/**
 * Every registry at once — the page to read before adding a badge anywhere, and the page that shows
 * when a new entry has picked an icon another entry already uses.
 */
export const EveryRegistry: Story = {
	render: (args: { def: StatusDef }) => (
		<div className="space-y-6">
			<Gallery heading="Delivery outcome" defs={DELIVERY_STATE_DEFS} />
			<Gallery heading="Delivery place" defs={DELIVERY_PLACE_DEFS} />
			<Gallery heading="Why withheld" defs={WITHHOLDING_FAMILY_DEFS} />
			<Gallery heading="Result" defs={ASSESSMENT_DEFS} />
			<Gallery heading="Severity" defs={SEVERITY_DEFS} />
			<Gallery heading="Practice status" defs={PRESENCE_DEFS} />
			<Gallery heading="Review status" defs={REVIEW_STATUS_DEFS} />
			<Gallery heading="Summary comment" defs={SUMMARY_POST_DEFS} />
			<section className="space-y-2">
				<h3 className="text-sm font-semibold">Selected in Controls</h3>
				<StatusBadge def={args.def} />
			</section>
		</div>
	),
	play: async ({ canvas }) => {
		// The words the delivery model turns on: neither the stored constant nor the old copy.
		canvas.getByText("Queued for conversation");
		canvas.getByText("Withheld");
		canvas.getByText("On the work");
	},
};

/**
 * A registry entry's label is prose, not a code point, so it can be long. The badge truncates rather
 * than pushing a table column open — the description beside it carries the full sentence.
 */
export const LongLabelTruncates: Story = {
	args: { def: DELIVERY_STATE_DEFS.PREPARED },
	render: (args: { def: StatusDef }) => (
		<div className="w-40 border p-2">
			<StatusBadge def={args.def} />
		</div>
	),
};
