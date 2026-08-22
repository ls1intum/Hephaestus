import type { ReactNode } from "react";
import { useState } from "react";
import { Drawer, DrawerContent } from "@/components/ui/drawer";
import { type DetailStackEntry, detailStackKey } from "./detail-stack";

export interface DetailDrawerLevel {
	depth: number;
	/** Whether a level is covering another, which is what decides "back" against "close". */
	nested: boolean;
}

export interface DetailDrawerStackProps<TKind extends string = string> {
	/** The open levels, outermost first. */
	stack: DetailStackEntry<TKind>[];
	/** Called with the depth to close down to — `close(0)` dismisses the whole stack. */
	onClose: (depth: number) => void;
	children: (entry: DetailStackEntry<TKind>, level: DetailDrawerLevel) => ReactNode;
}

/**
 * Each level renders the next *inside* its own content, so the child's portal nests in the parent's.
 * Base UI keeps nested portals out of the set it hides when a popup opens, which is what lets a
 * two-level stack arrive in one commit — from a deep link — and still reach the accessibility tree.
 *
 * A dismissal shuts the drawer first and navigates when the exit animation ends. Dropping the level
 * from the URL first would render it with an entry the caller no longer has data for, and would show
 * the previous entry's content on the way out if the URL replaced that depth. The address bar
 * therefore lags the animation; a browser Back inside that window wins and skips it.
 */
export function DetailDrawerStack<TKind extends string>({
	stack,
	onClose,
	children,
}: DetailDrawerStackProps<TKind>) {
	const [closingDepth, setClosingDepth] = useState<number | null>(null);

	function renderLevel(depth: number): ReactNode {
		const entry = stack[depth];
		if (!entry) return null;
		return (
			<Drawer
				key={detailStackKey(entry)}
				// Base UI shuts a parent's children anyway; this keeps the React tree in step.
				open={closingDepth === null || depth < closingDepth}
				swipeDirection="right"
				onOpenChange={(next) => {
					if (!next) setClosingDepth(depth);
				}}
				onOpenChangeComplete={(next) => {
					if (next || closingDepth !== depth) return;
					setClosingDepth(null);
					onClose(depth);
				}}
			>
				<DrawerContent size="detail" dimWhenNested={false}>
					{children(entry, { depth, nested: depth > 0 })}
					{renderLevel(depth + 1)}
				</DrawerContent>
			</Drawer>
		);
	}

	return renderLevel(0);
}
