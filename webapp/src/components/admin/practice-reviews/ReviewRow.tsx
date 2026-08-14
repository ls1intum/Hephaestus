import { Fragment, type ReactNode } from "react";
import type { StatusDef } from "@/components/practice-vocabulary/status-def";
import { statusToneClass } from "@/components/practice-vocabulary/status-def";
import { cn } from "@/lib/utils";

export interface ReviewRowProps {
	/**
	 * What this row is, as one registry entry: the leading icon and its tone both come from it.
	 * A whole entry rather than an icon and a colour, so a row cannot wear a green tick in the
	 * destructive tone — see rule 1 of the component rubric.
	 */
	status: StatusDef;
	/**
	 * The row's name, containing exactly one link. That link becomes the row's click target: it is
	 * stretched over the whole row by the CSS below, so the pointer target is the card while the
	 * accessibility tree still sees a single link with the title as its name.
	 */
	title: ReactNode;
	/** The facts that place the row — practice, work, who, when. One or two lines, no controls. */
	meta?: ReactNode;
	/**
	 * Status badges and the person, in fixed slots: right-aligned on a wide screen, wrapped under on a
	 * narrow one. See {@link ReviewRowChip} for why this is a list of reserved slots and not a fragment.
	 */
	chips?: ReviewRowChip[];
}

/**
 * One reserved position in a row's chip group.
 *
 * <p>The chips used to be a fragment in a `flex-wrap` box, so a row with no severity and a row with
 * one put their next badge at different x, and the eye had to re-find the column on every line. Two
 * of the four things an observation row shows are conditional, which means *most* rows differed.
 * A slot keeps its width when its `node` is absent, so the badge that follows it does not move —
 * the alignment a table gives for free (NN/g, "Data Tables"), on a list that is not a table.
 *
 * <p>The width is the caller's because it belongs to the list, not to the row: every row of one
 * list passes the same slots in the same order, and that is what makes the column constant. The
 * widths are `lg:`-prefixed, so below that the chips wrap as they always did and an empty slot
 * collapses: reserving four columns' worth of space needs a screen wide enough to hold the whole
 * strip, and forcing it on a tablet would push the row wider than the page.
 */
export interface ReviewRowChip {
	key: string;
	/** Reserved width from `lg` up, e.g. `"lg:w-28"`. Constant across every row of one list. */
	width: string;
	/** What sits in the slot. Absent keeps the space and shows nothing. */
	node?: ReactNode;
}

/**
 * One row of a practice-review list, in the one shape all three lists use.
 *
 * <p>Each of these lists used to ship twice: a `<table>` behind `xl:` and an `ItemGroup` of cards
 * behind `xl:hidden`, with different fields, different order and different words in each. Two
 * renderings of a 25-row list where a single cell carries the meaning is two things to keep in step
 * and one of them always drifts — which is how the observations table grew a "Developer and reviewed
 * work" column that the card version split in two, and how the skeleton came to match neither.
 *
 * <p>A table earns its keep when a reader compares the same cell down a column, and the row's name —
 * a sentence of prose, of any length — is not comparable in that way. So this is one row that
 * reflows, and the tables are gone. What *is* comparable down the list keeps a column anyway: the
 * chips sit in fixed slots (see {@link ReviewRowChip}) and a tally is drawn as a fixed grid (see
 * `ReviewCountStrip`), so a badge and a number each land at one x on every row without a `<table>`
 * around them.
 *
 * <h4>Why the whole row is not a link</h4>
 * The obvious construction — `<Item render={<Link/>}>` — makes the row itself an anchor, and then a
 * nested link is invalid HTML. These rows need nested links: the practice name opens its definition,
 * the reviewed work opens the pull request. So the title carries the only anchor and
 * `after:absolute after:inset-0` stretches its hit area over the row. Anything else interactive sits
 * above it with `relative` and keeps its own target.
 */
export function ReviewRow({ status, title, meta, chips }: ReviewRowProps) {
	const { icon: Icon } = status;
	return (
		<li className="relative flex items-start gap-3 p-4 transition-colors hover:bg-muted/40 has-[a:focus-visible]:bg-muted/40">
			<span
				aria-hidden
				className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-md border bg-background"
			>
				<Icon className={cn("size-4", statusToneClass(status.badgeVariant))} />
			</span>
			<div className="flex min-w-0 flex-1 flex-wrap items-start justify-between gap-x-4 gap-y-2">
				<div className="min-w-0 flex-1 basis-64 space-y-1">
					{/* The stretched hit area lives here so it covers the row, not just the words. */}
					<div className="text-sm font-medium [&_a]:after:absolute [&_a]:after:inset-0 [&_a:hover]:underline">
						{title}
					</div>
					{meta && <div className="min-w-0 space-y-0.5 text-xs text-muted-foreground">{meta}</div>}
				</div>
				{chips && chips.length > 0 && (
					<div className="relative flex flex-wrap items-start gap-1.5 lg:flex-nowrap">
						{chips.map((chip) => (
							<span
								key={chip.key}
								className={cn(
									"flex min-w-0 flex-wrap items-center gap-1.5 empty:hidden lg:shrink-0 lg:empty:flex",
									chip.width,
								)}
							>
								{chip.node}
							</span>
						))}
					</div>
				)}
			</div>
		</li>
	);
}

export interface ReviewRowListProps {
	/** Names the list for a screen reader, since the rows carry no visible heading. */
	label: string;
	children: ReactNode;
}

export function ReviewRowList({ label, children }: ReviewRowListProps) {
	return (
		<ul aria-label={label} className="divide-y rounded-lg border">
			{children}
		</ul>
	);
}

/**
 * One meta line, dot-separated.
 *
 * Takes the pieces rather than pre-joined children so the separators are placed here: a caller that
 * writes its own `·` between optional pieces leaves a dangling one the day a piece is absent, which
 * is how a row ends up reading "Thin controllers · · observed 3 days ago". Falsy entries drop out,
 * so a caller can pass a conditional straight in.
 */
export function ReviewRowMeta({ items }: { items: ReactNode[] }) {
	const shown = items.filter(Boolean);
	if (shown.length === 0) return null;
	return (
		<p className="flex min-w-0 flex-wrap items-center gap-x-1.5 gap-y-0.5 break-words">
			{shown.map((item, index) => (
				// The pieces of one line have no identity of their own and never reorder: this list is
				// rebuilt from the row's fields on every render, in a fixed order.
				<Fragment key={index}>
					{index > 0 && <span aria-hidden>·</span>}
					{item}
				</Fragment>
			))}
		</p>
	);
}
