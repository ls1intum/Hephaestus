import type { LucideIcon } from "lucide-react";
import type { FacetOption } from "@/components/common/FacetMultiSelect";

/**
 * One entry in a status registry: everything a surface needs to render one value of one enum.
 *
 * Every screen that shows a status — a table cell, a filter dropdown, a select item, an empty state,
 * a detail header — reads the same entry, so the four of them cannot drift. Before this existed each
 * screen kept its own label map and its own `switch` returning a badge variant, which is why the
 * Delivery filter dropdown rendered plain grey text next to a table of coloured tags.
 *
 * `icon` is not decoration and not optional. Colour is never the only channel that carries meaning
 * (WCAG 2.2 SC 1.4.1), and the badge variants collapse: two severities share `destructive`, three
 * statuses share `outline`. The icon is what still separates them in greyscale and under a filter.
 */
export interface StatusDef {
	/** Operator-facing words. Sentence case, and never the wire constant. */
	label: string;
	/** Second channel alongside colour; distinct within its own enum. */
	icon: LucideIcon;
	badgeVariant: BadgeVariant;
	/**
	 * One sentence saying what the value means, for a detail surface, a tooltip or an empty state.
	 * Not a restatement of `label` — it earns its place by saying something the label cannot.
	 *
	 * Deliberately not rendered in the filter dropdown: a facet item is one truncated line, so a
	 * sentence there arrives as a fragment. The icon and the words are what carry that surface.
	 */
	description: string;
}

export type BadgeVariant =
	| "default"
	| "secondary"
	| "destructive"
	| "outline"
	| "success"
	| "warning";

/**
 * A total registry over an enum. `Record` rather than `Partial<Record>` on purpose: a value the
 * server adds fails `typecheck:webapp` on the registry that has no words for it, instead of
 * rendering as a blank cell in production.
 */
export type StatusDefs<TValue extends string> = Record<TValue, StatusDef>;

/**
 * Icon colour matching a badge variant, for the places an icon appears outside a badge — filter
 * items, the trace rail. Keeps the dropdown's visual identity the same as the tag's.
 */
const TONE_CLASS: Record<BadgeVariant, string> = {
	default: "text-primary",
	secondary: "text-muted-foreground",
	destructive: "text-destructive",
	outline: "text-muted-foreground",
	success: "text-success",
	warning: "text-warning",
};

export function statusToneClass(variant: BadgeVariant): string {
	return TONE_CLASS[variant];
}

/**
 * The registry's values in declaration order, for a schema allowlist or a story that walks them all.
 * Declaration order is the order a reader should meet them in, so it is also the filter order.
 *
 * <p>Typed as a non-empty tuple so it drops straight into `z.enum(...)`, which will not take a plain
 * array. The assertion holds by construction: `StatusDefs` is a total `Record` over a union of
 * string literals, and no such union is empty.
 */
export function statusValues<TValue extends string>(
	defs: StatusDefs<TValue>,
): [TValue, ...TValue[]] {
	return Object.keys(defs) as [TValue, ...TValue[]];
}

/**
 * Filter options built from the registry, so a dropdown cannot say something a badge does not.
 *
 * <p>Every value, in registry order. A facet that wants fewer filters the result — one caller does,
 * the delivery place filter, and it says why at its call site. That is a fact about that one screen
 * and it used to live here as an `only` parameter whose own documentation named its single caller,
 * which is the shape of a prop that should have been a `.filter()`.
 */
export function statusFacetOptions<TValue extends string>(
	defs: StatusDefs<TValue>,
): FacetOption<TValue>[] {
	return statusValues(defs).map((value) => {
		const def = defs[value];
		return {
			value,
			label: def.label,
			icon: def.icon,
			iconClassName: statusToneClass(def.badgeVariant),
		};
	});
}
