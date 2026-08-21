import { createContext, type ReactNode, use, useEffect, useState } from "react";
import { Drawer, DrawerContent } from "@/components/ui/drawer";
import { type DetailStackEntry, detailStackKey } from "./detail-stack";

/**
 * Detail drawers are deliberately much wider than {@link import("@/components/ui/sheet").Sheet}:
 * they replace a full page, so they have to hold what that page held. Full width below `sm` because
 * a partial cover on a phone is unreadable.
 *
 * Marked important because the primitive sets its own default behind a `data-swipe-axis` selector,
 * which a plain utility loses to on specificity rather than on source order.
 */
const DETAIL_DRAWER_WIDTH =
	"[--drawer-content-width:100%]! sm:[--drawer-content-width:min(44rem,92vw)]! xl:[--drawer-content-width:min(62rem,75vw)]!";

interface DetailDrawerLevel {
	depth: number;
	close: () => void;
}

const DetailDrawerLevelContext = createContext<DetailDrawerLevel | null>(null);

export function useDetailDrawerLevel(): DetailDrawerLevel {
	const level = use(DetailDrawerLevelContext);
	if (!level) throw new Error("useDetailDrawerLevel must be used inside a DetailDrawerStack.");
	return level;
}

export interface DetailDrawerStackProps {
	/** The open levels, outermost first. An empty stack renders nothing. */
	stack: DetailStackEntry[];
	/** Called with the depth to close down to — `close(0)` dismisses the whole stack. */
	onClose: (depth: number) => void;
	children: (entry: DetailStackEntry, depth: number) => ReactNode;
}

/**
 * Renders a stack of right-hand detail drawers over the page that owns it.
 *
 * Base UI decides whether a drawer is nested from the React tree rather than the DOM, so each level
 * renders the next as its own child and inherits the stacking for free: the drawers behind the
 * frontmost one step back and dim, and Escape or a rightward swipe pops exactly one level.
 */
export function DetailDrawerStack({ stack, onClose, children }: DetailDrawerStackProps) {
	// Levels mount one frame apart. Base UI hides everything outside the topmost popup when a drawer
	// opens, and two levels opening in the same commit each hide the other — leaving a stack that is
	// on screen but absent from the accessibility tree. That only happens on a deep link into a
	// multi-level stack, which is exactly the case a shared URL produces.
	const [mountedDepth, setMountedDepth] = useState(Math.min(stack.length, 1));
	const openDepth = Math.min(mountedDepth, stack.length);

	useEffect(() => {
		if (mountedDepth === stack.length) return;
		const frame = requestAnimationFrame(() =>
			setMountedDepth(mountedDepth < stack.length ? mountedDepth + 1 : stack.length),
		);
		return () => cancelAnimationFrame(frame);
	}, [mountedDepth, stack.length]);

	function renderLevel(depth: number): ReactNode {
		const entry = stack[depth];
		if (!entry || depth >= openDepth) return null;
		return (
			<Drawer
				key={detailStackKey(entry)}
				open
				swipeDirection="right"
				onOpenChange={(open) => {
					if (!open) onClose(depth);
				}}
			>
				<DrawerContent className={DETAIL_DRAWER_WIDTH}>
					<DetailDrawerLevelContext value={{ depth, close: () => onClose(depth) }}>
						{children(entry, depth)}
					</DetailDrawerLevelContext>
				</DrawerContent>
				{renderLevel(depth + 1)}
			</Drawer>
		);
	}

	return renderLevel(0);
}
