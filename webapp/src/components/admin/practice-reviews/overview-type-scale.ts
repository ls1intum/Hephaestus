/**
 * The overview's type scale: one size, weight and colour per role, so the same kind of text never
 * switches between grey and black or between sizes from one card to the next. The rule for colour:
 * what the reader reads off, a number or a title, is `text-foreground`; what describes it, a label,
 * a noun, a caption or a note, is `text-muted-foreground`. A figure with nothing in it, a zero or
 * "None", is muted and regular, so the numbers that matter stand out.
 *
 * Section titles and their descriptions are `Section`'s, card titles `CardTitle`'s, table heads and
 * cells the table's; this file holds the roles inside the cards.
 */
export const OVERVIEW_TYPE = {
	/** A card's main figure: "284", "11 min". */
	hero: "text-2xl leading-none font-semibold tabular-nums text-foreground",
	/** The words after the main figure: "reviews", "median, start to comment". */
	heroNoun: "text-sm text-muted-foreground",
	/** The main figure set against the period before, and other one-line asides under it. */
	aside: "text-xs text-muted-foreground",
	/** A group's caption inside a card: "Delivered by channel". */
	caption: "text-xs font-medium text-muted-foreground",
	/** A figure's label: "Waiting", "On GitHub". */
	label: "text-sm text-muted-foreground",
	/** A figure's number, a link or not. */
	value: "text-sm font-semibold tabular-nums text-foreground",
	/** A figure with nothing in it. */
	none: "text-sm font-normal tabular-nums text-muted-foreground",
	/** A line that says there is nothing to show, or what a card measured instead. */
	empty: "text-sm text-muted-foreground",
	/** The details beside a listed item: who, how long. */
	meta: "text-xs text-muted-foreground",
	/** A note under a table or a card, such as why two numbers differ. */
	note: "text-xs text-muted-foreground",
} as const;
