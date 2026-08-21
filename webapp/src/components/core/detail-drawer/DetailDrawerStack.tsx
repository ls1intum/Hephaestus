import { type ReactNode, useEffect, useState } from "react";
import { Drawer, DrawerContent } from "@/components/ui/drawer";
import { type DetailStackEntry, detailStackKey } from "./detail-stack";

/**
 * Detail drawers are deliberately much wider than {@link import("@/components/ui/sheet").Sheet}:
 * they replace a full page, so they have to hold what that page held. Full width below `sm`, because
 * a partial cover on a phone is unreadable.
 *
 * `--peek` is well above the primitive's default so a covered panel keeps a column on screen rather
 * than a hairline — that column is the whole reason to stack instead of replace. Marked important
 * because the primitive sets both behind a `data-swipe-axis` selector, which a plain utility loses
 * to on specificity rather than on source order.
 */
const DETAIL_DRAWER_CLASS =
	"[--peek:2.5rem]! [--drawer-content-width:100%]! sm:[--drawer-content-width:min(44rem,92vw)]! xl:[--drawer-content-width:min(62rem,75vw)]!";

/** What a level knows about its own position in the stack. */
export interface DetailDrawerLevel {
	depth: number;
	/** Whether a level is covering another, which is what decides "back" against "close". */
	nested: boolean;
}

export interface DetailDrawerStackProps<TKind extends string = string> {
	/** The open levels, outermost first. An empty stack renders nothing. */
	stack: DetailStackEntry<TKind>[];
	/** Called with the depth to close down to — `close(0)` dismisses the whole stack. */
	onClose: (depth: number) => void;
	children: (entry: DetailStackEntry<TKind>, level: DetailDrawerLevel) => ReactNode;
}

/**
 * Renders a stack of right-hand detail drawers over the page that owns it.
 *
 * Base UI decides whether a drawer is nested from the React tree rather than the DOM, so each level
 * renders the next as its own child and inherits the stacking for free: the drawers behind the
 * frontmost step back, and Escape, a rightward swipe or a press on the page all pop one level.
 *
 * Two things here are deliberately out of step with the URL, in opposite directions:
 *
 * - **Opening**, levels appear one frame apart. Base UI hides everything outside the frontmost popup
 *   when a drawer opens, and two levels opening in the same commit each hide the other — leaving a
 *   stack that is on screen but absent from the accessibility tree. Only a deep link mounts two at
 *   once, but the same one-at-a-time reveal also keeps a hand-opened level from animating on top of
 *   a parent that has not settled.
 * - **Closing**, the drawer shuts first and the URL follows when the exit animation finishes. The
 *   obvious alternative — let the URL drop the level and keep rendering the old entry while it
 *   leaves — was tried and is worse in two ways: the stack would call `children` for a level the
 *   caller no longer has data for (the caller sizes its per-level queries from the same URL stack,
 *   so it indexes past the end and throws), and a retained entry goes stale the moment the URL
 *   replaces an entry at that depth, so the panel animates out showing the *previous* level's
 *   content. Holding the navigation instead means `children` only ever sees a live entry.
 *
 * The cost is that the address bar lags the animation by the length of the transition. A browser
 * Back during that window wins, and the level disappears without animating — which is the right
 * outcome, because Back is its own transition.
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
				// A level closing at `closingDepth` takes the levels above it with it, which is what
				// Base UI does anyway when a parent drawer shuts.
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
