import { createContext, type ReactNode, use } from "react";
import { createPortal } from "react-dom";

import { rendersContent } from "@/lib/react-node";

/** The place above the tabs, and the tracked record the layout shows in it. */
interface GlobalScopeTarget {
	/** The element above the tabs; `null` until it is mounted. */
	element: HTMLElement | null;
	/** The tracked record (`ReviewSelectionBar`), shown after the scope's controls. */
	selection: ReactNode;
}

const GlobalScopeContext = createContext<GlobalScopeTarget | undefined>(undefined);

export interface GlobalScopeSlotProps extends GlobalScopeTarget {
	children: ReactNode;
}

/**
 * Gives every tab under it the place above the tabs, where `GlobalScopeRow` draws the scope. The
 * layout owns the place and the tracked record; the tab owns the scope's controls, which need its
 * own sources and names.
 */
export function GlobalScopeSlot({ element, selection, children }: GlobalScopeSlotProps) {
	return <GlobalScopeContext value={{ element, selection }}>{children}</GlobalScopeContext>;
}

export interface GlobalScopeRowProps {
	/** The scope's facets and "Clear scope". */
	controls: ReactNode;
	/** The scope's pills: values folded below `sm`, the record filtered to, and the parts not applied. */
	pills: ReactNode;
	/** True when the row has nothing to show unless a record is tracked: no control and no value. */
	empty?: boolean;
}

const LABEL = "Applies to every tab";

/**
 * The narrowing that every tab keeps, in one row above the tabs: the scope, "Clear scope" and the
 * tracked record. It says that it applies to every tab, so the reader does not read it as a filter
 * of the tab below. Outside a `GlobalScopeSlot`, as in a story of one tab, the row is drawn in place.
 */
export function GlobalScopeRow({ controls, pills, empty = false }: GlobalScopeRowProps) {
	const target = use(GlobalScopeContext);
	const selection = target?.selection;
	if (empty && !rendersContent(selection)) {
		return null;
	}
	const row = (
		<div role="group" aria-label={LABEL} className="space-y-2">
			{/* Chips wrap side by side at every width: a phone fits two or three to a line. */}
			<div className="flex flex-wrap items-center gap-2">
				<span className="basis-full text-xs font-medium text-muted-foreground sm:basis-auto">
					{LABEL}
				</span>
				{controls}
				{selection}
			</div>
			<div className="flex flex-wrap gap-2 empty:hidden">{pills}</div>
		</div>
	);
	if (target === undefined) {
		return row;
	}
	// The place mounts in the same commit as the tab, so the row follows it before the first paint.
	return target.element === null ? null : createPortal(row, target.element);
}
