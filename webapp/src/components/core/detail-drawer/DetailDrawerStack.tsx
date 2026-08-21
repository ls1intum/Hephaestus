import { type ReactNode, useEffect, useState } from "react";
import { Drawer, DrawerContent } from "@/components/ui/drawer";
import { type DetailStackEntry, detailStackKey } from "./detail-stack";

/**
 * Important because the primitive sets both behind a `data-swipe-axis` selector — a plain utility
 * loses on specificity, not on source order. `--peek` is far above the primitive's default: the
 * column a covered panel keeps on screen is the reason to stack rather than replace.
 */
const DETAIL_DRAWER_CLASS =
	"[--peek:2.5rem]! [--drawer-content-width:100%]! sm:[--drawer-content-width:min(44rem,92vw)]! xl:[--drawer-content-width:min(62rem,75vw)]!";

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
 * What is mounted lags the URL in both directions, for two unrelated reasons.
 *
 * **Opening**, levels appear one frame apart: Base UI hides everything outside the frontmost popup,
 * so two levels opening in one commit each hide the other, leaving a stack that is on screen and
 * absent from the accessibility tree.
 *
 * **Closing**, the drawer shuts and the URL follows when the exit animation ends. Letting the URL
 * drop the level first means calling `children` for a level the caller no longer has data for — it
 * sizes per-level queries from the same stack, so it indexes past the end and throws — and a
 * retained entry goes stale as soon as the URL replaces that depth. The cost is that the address bar
 * lags the animation; a browser Back inside that window wins and skips it, which is correct.
 */
export function DetailDrawerStack<TKind extends string>({
	stack,
	onClose,
	children,
}: DetailDrawerStackProps<TKind>) {
	const [revealedDepth, setRevealedDepth] = useState(Math.min(stack.length, 1));
	const [closingDepth, setClosingDepth] = useState<number | null>(null);
	const openDepth = Math.min(revealedDepth, stack.length);

	useEffect(() => {
		if (revealedDepth >= stack.length) return;
		const frame = requestAnimationFrame(() => setRevealedDepth(revealedDepth + 1));
		return () => cancelAnimationFrame(frame);
	}, [revealedDepth, stack.length]);

	function renderLevel(depth: number): ReactNode {
		const entry = stack[depth];
		if (!entry || depth >= openDepth) return null;
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
				<DrawerContent className={DETAIL_DRAWER_CLASS} dimWhenNested={false}>
					{children(entry, { depth, nested: depth > 0 })}
				</DrawerContent>
				{renderLevel(depth + 1)}
			</Drawer>
		);
	}

	return renderLevel(0);
}
