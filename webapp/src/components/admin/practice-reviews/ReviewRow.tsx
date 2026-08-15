import { Fragment, type ReactNode } from "react";
import type { StatusDef } from "@/components/practice-vocabulary/status-def";
import { statusToneClass } from "@/components/practice-vocabulary/status-def";
import { cn } from "@/lib/utils";

export interface ReviewRowProps {
	/** A whole registry entry rather than an icon and a colour, so a row cannot wear a green tick in
	 * the destructive tone. */
	status: StatusDef;
	/**
	 * The row's name, which must contain exactly one link: that link's hit area is stretched over the
	 * whole row by the CSS below, so the pointer target is the card while the accessibility tree still
	 * sees a single link named by the title.
	 */
	title: ReactNode;
	/** Facts that place the row. No controls — the title's hit area covers this area. */
	meta?: ReactNode;
	/** See {@link ReviewRowChip}: reserved slots and free ones, not a fragment. */
	chips?: ReviewRowChip[];
}

/**
 * One position in a row's chip group, either **reserved** or **free**.
 *
 * <p>A reserved chip carries a `width` and keeps it when its `node` is absent, so a conditional
 * badge does not shift the badge after it and the column holds down the list — the alignment a table
 * gives for free, on a list that is not a table. The width is the caller's because it belongs to the
 * list: the column is only constant if every row of one list passes the same slots in the same
 * order. Reserving space needs a screen wide enough for the whole strip, so widths are
 * `lg:`-prefixed and below that the chips simply wrap.
 *
 * <p>A free chip has no `width`, takes only the space its badges need, and takes none at all when it
 * is empty. **Reserve a slot only for a fact that is on nearly every row** — a column that is
 * usually blank aligns nothing and charges the whole list its width, which is what made these rows
 * look mostly empty. Exception badges are free chips.
 *
 * <p>Because the strip sits at the row's right edge, free chips are passed **before** the reserved
 * ones: they then flow leftwards into the gap after the title, and the reserved columns keep the
 * same x on every row whether or not the exception fired.
 */
export interface ReviewRowChip {
	key: string;
	/**
	 * Reserved width from `lg` up, e.g. `"lg:w-44"`, constant across every row of one list. Omit for
	 * a chip that should take only the width it needs.
	 */
	width?: string;
	/** Absent shows nothing: a reserved slot keeps its space, a free chip takes none. */
	node?: ReactNode;
}

/**
 * One reflowing row rather than a `<table>`: a table earns its keep when a reader compares the same
 * cell down a column, and the row's name is a sentence of prose of any length. What *is* comparable
 * and usually present keeps a column anyway — those chips sit in fixed slots (see
 * {@link ReviewRowChip}) and a tally is drawn as a fixed grid (see `ReviewCountStrip`).
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
									"flex min-w-0 flex-wrap items-center gap-1.5 empty:hidden",
									// `lg:empty:flex` is what re-shows an empty reserved slot so it can hold its
									// width; a free chip keeps `empty:hidden` at every width and, having no
									// `shrink-0`, gives way to the reserved columns before the row can overflow.
									chip.width && "lg:shrink-0 lg:empty:flex",
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
 * Takes the pieces rather than pre-joined children so the separators are placed here: a caller that
 * writes its own `·` between optional pieces leaves a dangling one the day a piece is absent. Falsy
 * entries drop out, so a caller can pass a conditional straight in.
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
