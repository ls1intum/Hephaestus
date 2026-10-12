import { Fragment, type ReactElement, type ReactNode } from "react";

import { cn } from "cn";
import { InlineLink } from "@/components/common/InlineLink";
import type { StatusDef } from "@/components/common/status-def";
import type { DetailStackEntry } from "@/components/layout/detail-drawer/detail-stack";
import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";
import { StatusGlyph } from "@/components/practice-vocabulary/StatusTooltip";
import { Item, ItemActions, ItemContent, ItemMedia, ItemTitle } from "@/components/ui/item";
import { hasText } from "@/lib/text";

export interface ReviewRowProps {
	/**
	 * What the row's record is, drawn as the leading icon in its tone and named by its label for a
	 * screen reader. It is no button: the row's one control is its title. It is not repeated as a
	 * badge: the trailing column carries only what qualifies it.
	 */
	status: StatusDef;
	/** The row's one link, stretched over the whole row; {@link ReviewRowLink} draws it. */
	title: ReactNode;
	meta?: ReactElement | undefined;
	/** People and qualifying badges, in the trailing column. */
	chips?: ReviewRowChip[];
	/**
	 * The row's Track and Trace (`RecordRowActions`), last in the row and apart from its link. A row
	 * whose record is tracked is tinted, read off the pressed Track toggle.
	 */
	actions?: ReactElement;
}

export interface ReviewRowChip {
	key: string;
	/**
	 * A width reserved from `lg` up, for a fact every row carries, so it sits at one x down the list
	 * however long its neighbour is. A chip only some rows carry takes none and collapses when empty.
	 */
	width?: string;
	node?: ReactNode;
}

/**
 * One record in a divided list, drawn the way Activity draws a piece of open work: the status icon,
 * the title that opens the record's sheet over the page, the facts that place it, at the end who it
 * is about and what qualifies it, and last its Track and Trace. The title is the row's one link: the
 * facts are plain text.
 */
export function ReviewRow({ status, title, meta, chips, actions }: ReviewRowProps) {
	return (
		<Item render={<li />} variant="row" size="sm" className="relative items-start">
			{/* The tracked record's row is tinted, read off its pressed Track toggle. */}
			<span
				aria-hidden
				className="pointer-events-none absolute inset-0 hidden bg-primary/5 group-has-[[data-track][aria-pressed=true]]/item:block"
			/>
			{/* The open row keeps a bar on its leading edge, as an open practice row does. It is drawn
			    from the title link's `aria-current`, so what a screen reader hears and what the eye sees
			    are one fact. */}
			<span
				aria-hidden
				className="absolute inset-y-0 left-0 hidden w-0.5 bg-mentor group-has-[[data-slot=item-title]_[aria-current]]/item:block"
			/>
			<ItemMedia className="mt-0.5">
				<StatusGlyph def={status} className="size-4 justify-center" />
			</ItemMedia>
			<ItemContent className="min-w-0 basis-48 gap-1">
				<ItemTitle className="line-clamp-none block w-full min-w-0 break-words [&_a]:after:absolute [&_a]:after:inset-0">
					{title}
				</ItemTitle>
				{meta !== undefined && (
					<div className="min-w-0 space-y-0.5 text-xs text-muted-foreground">{meta}</div>
				)}
			</ItemContent>
			{chips &&
				chips.length > 0 && (
					// `relative` lifts the column over the stretched link, so a person's name can be selected.
					<ItemActions className="relative flex-wrap items-start gap-x-3 gap-y-1.5 lg:flex-nowrap">
						{chips.map((chip) => (
							<span
								key={chip.key}
								className={cn(
									"flex min-w-0 flex-wrap items-center gap-1.5 empty:hidden",
									hasText(chip.width) && "lg:shrink-0 lg:empty:flex",
									chip.width,
								)}
							>
								{chip.node}
							</span>
						))}
					</ItemActions>
				)}
			{actions !== undefined && <div className="shrink-0 self-start">{actions}</div>}
		</Item>
	);
}

export interface ReviewRowLinkProps {
	/** The level the row opens over the page. */
	entry: DetailStackEntry;
	className?: string;
	children: ReactNode;
}

/**
 * A row's title: a link to its level, drawn to the house link rule. While that level is in front of
 * the detail stack the link points at the address already shown, so the router marks it
 * `aria-current="page"`, which the row's bar reads.
 */
export function ReviewRowLink({ entry, className, children }: ReviewRowLinkProps) {
	return (
		<InlineLink render={<DetailStackLink entry={entry} single />} className={className}>
			{children}
		</InlineLink>
	);
}

export interface ReviewRowListProps {
	label: string;
	children: ReactNode;
}

export function ReviewRowList({ label, children }: ReviewRowListProps) {
	return (
		<ul aria-label={label} className="overflow-hidden rounded-xl border bg-card">
			{children}
		</ul>
	);
}

export function ReviewRowMeta({ items }: { items: ReactNode[] }) {
	const shown = items.filter(Boolean);
	if (shown.length === 0) {
		return null;
	}
	return (
		<p className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-0.5 break-words">
			{shown.map((item, index) => (
				<Fragment key={index}>
					{index > 0 && (
						<span aria-hidden className="text-muted-foreground/50">
							·
						</span>
					)}
					{item}
				</Fragment>
			))}
		</p>
	);
}
