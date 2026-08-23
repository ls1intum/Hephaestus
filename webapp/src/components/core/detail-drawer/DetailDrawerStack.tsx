import type { ReactNode } from "react";
import { useLayoutEffect, useState } from "react";
import { Drawer, DrawerContent } from "@/components/ui/drawer";
import { type DetailStackEntry, detailStackKey } from "./detail-stack";

/**
 * The ways a drawer is left without deciding anything. `swipe` is one of them, and it is the reason
 * a guarded level cannot simply drop `swipeDirection`: that prop is not a swipe toggle, it is which
 * edge the drawer belongs to, and omitting it defaults to `down` — a bottom sheet.
 */
const CASUAL_DISMISSALS = ["escape-key", "outside-press", "focus-out", "swipe"];

export interface DetailDrawerLevel {
	depth: number;
	/** Whether a level is covering another, which is what decides "back" against "close". */
	nested: boolean;
}

export interface DetailDrawerStackProps<TKind extends string = string> {
	/** The open levels, outermost first. */
	stack: DetailStackEntry<TKind>[];
	/**
	 * Kinds that close only through their own controls. A level holding unsaved work cannot also be
	 * dismissed by Escape, a press on the page or a swipe: those gestures are how a drawer is normally
	 * left, and here they would discard the work without asking.
	 */
	guardedKinds?: readonly TKind[];
	/** Called with the depth to close down to — `close(0)` dismisses the whole stack. */
	onClose: (depth: number) => void;
	children: (entry: DetailStackEntry<TKind>, level: DetailDrawerLevel) => ReactNode;
}

/**
 * Each level renders the next *inside* its own content, so the child's portal nests in the parent's.
 * Base UI keeps nested portals out of the set it hides when a popup opens, which is what lets a
 * stack reach the accessibility tree at every depth.
 *
 * A dismissal shuts the drawer first and navigates when the exit animation ends. Dropping the level
 * from the URL first would render it with an entry the caller no longer has data for, and would show
 * the previous entry's content on the way out if the URL replaced that depth. The address bar
 * therefore lags the animation; a browser Back inside that window wins and skips it.
 */
export function DetailDrawerStack<TKind extends string>({
	stack,
	guardedKinds,
	onClose,
	children,
}: DetailDrawerStackProps<TKind>) {
	const [closingDepth, setClosingDepth] = useState<number | null>(null);

	// The URL lags the animation, so the level that just finished leaving is still in `stack`.
	// Clearing this on the completion frame instead re-opens it for however long the navigation
	// takes — it pops back in, and the level behind it snaps to its stepped-back position and
	// animates forward a second time.
	if (closingDepth !== null && stack.length <= closingDepth) {
		setClosingDepth(null);
	}

	// Not rendered at all until there is a level, so mounting the component *is* the level arriving —
	// which is what `useArrived` needs. A component that stays mounted rendering `null` has already
	// spent its first render by the time an entry shows up.
	if (stack.length === 0) return null;
	return (
		<DetailDrawerLevelView
			depth={0}
			stack={stack}
			guardedKinds={guardedKinds}
			closingDepth={closingDepth}
			setClosingDepth={setClosingDepth}
			onClose={onClose}
		>
			{children}
		</DetailDrawerLevelView>
	);
}

interface DetailDrawerLevelViewProps<TKind extends string> extends DetailDrawerStackProps<TKind> {
	depth: number;
	closingDepth: number | null;
	setClosingDepth: (depth: number | null) => void;
}

/**
 * One level. A component rather than a loop in the parent because it owns a hook: opening has to be
 * a state change on a mounted drawer, and each level mounts at a different time.
 */
function DetailDrawerLevelView<TKind extends string>({
	depth,
	stack,
	guardedKinds,
	closingDepth,
	setClosingDepth,
	onClose,
	children,
}: DetailDrawerLevelViewProps<TKind>) {
	const entry = stack[depth];
	const arrived = useArrived();
	if (!entry) return null;

	const child = stack[depth + 1];
	const guarded = guardedKinds?.includes(entry.kind) ?? false;
	return (
		<Drawer
			key={detailStackKey(entry)}
			// Base UI shuts a parent's children anyway; this keeps the React tree in step.
			open={arrived && (closingDepth === null || depth < closingDepth)}
			swipeDirection="right"
			onOpenChange={(next, details) => {
				if (next) return;
				if (guarded) {
					if (CASUAL_DISMISSALS.includes(details.reason as string)) return;
					// Straight to the URL, skipping the exit animation the other levels get. A guarded
					// level holds a draft and `useUnsavedChanges` blocks the navigation to ask about it,
					// so animating out first would unmount the form while the prompt is still on screen —
					// and "Keep editing" would come back to an empty one.
					onClose(depth);
					return;
				}
				setClosingDepth(depth);
			}}
			onOpenChangeComplete={(next) => {
				if (next || closingDepth !== depth) return;
				onClose(depth);
			}}
		>
			<DrawerContent size="detail" dimWhenNested={false}>
				{children(entry, { depth, nested: depth > 0 })}
				{child && (
					<DetailDrawerLevelView
						depth={depth + 1}
						stack={stack}
						guardedKinds={guardedKinds}
						closingDepth={closingDepth}
						setClosingDepth={setClosingDepth}
						onClose={onClose}
					>
						{children}
					</DetailDrawerLevelView>
				)}
			</DrawerContent>
		</Drawer>
	);
}

/**
 * False for the level's first render, true from the commit onwards.
 *
 * Base UI derives its enter transition from `open` *changing* on a mounted drawer:
 * `useTransitionStatus` seeds `mounted` from `open`, so `open && !mounted` — the branch that sets
 * `starting` — never runs for a drawer that mounts already open. It then gets no
 * `data-starting-style` frame and appears at its final position with no transition at all. Levels
 * are mounted and unmounted by the URL, so every one of them lands on that path.
 *
 * A layout effect, not a frame. All Base UI needs is for `open` to change on a Root that is already
 * mounted — it owns the `data-starting-style` frame itself — and flipping during the commit keeps
 * the panel queryable in the same tick it was asked for, which a `requestAnimationFrame` does not:
 * that frame can land after a caller has already looked for the panel and found nothing.
 */
function useArrived(): boolean {
	const [arrived, setArrived] = useState(false);
	useLayoutEffect(() => setArrived(true), []);
	return arrived;
}
