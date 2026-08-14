import { type ReactNode, useState } from "react";

/**
 * Somewhere for a controlled component to put its answer, so its controls work in Storybook.
 *
 * <h4>The defect this exists to prevent</h4>
 * A controlled component renders from a value prop and reports changes through a callback; the route
 * that owns it puts the result back. A story that passes a frozen value and `fn()` — a spy that
 * records the call and changes nothing — has removed the second half of that loop. Every control on
 * the story is then inert: choosing a severity leaves the facet unselected, clicking two days in a
 * calendar highlights neither, a switch will not move, and a tab will not change panel. Nothing looks
 * broken, because a spy is a perfectly good `onChange` — it is the *pairing* with an unchanging value
 * that is wrong.
 *
 * <p>That pairing is what the product owner hit when he reported he could not pick a date range on
 * the Observations screen. The control was there and correctly wired; the story it was in could not
 * accept an answer. It was found on three list screens, then on seven more surfaces.
 *
 * <p>`fn()` is still right where a callback has no paired value — `onDelete`, `onRetry`, `onSubmit`.
 * It is only wrong when something on screen is supposed to change as a result.
 *
 * <p>A story still spreads `args` through this, so Controls keeps working and the initial value is
 * editable from the panel.
 */
export function Stateful<T>({
	initial,
	children,
}: {
	initial: T;
	children: (value: T, setValue: (next: T) => void) => ReactNode;
}) {
	const [value, setValue] = useState(initial);
	return <>{children(value, setValue)}</>;
}

/**
 * {@link Stateful} for a component that reports a *patch* rather than a whole value — the shape every
 * list screen's `onSearchChange` uses, because the route merges the patch into the URL's search.
 */
export function StatefulPatch<T extends object>({
	initial,
	children,
}: {
	initial: T;
	children: (value: T, patch: (next: Partial<T>) => void) => ReactNode;
}) {
	const [value, setValue] = useState(initial);
	return <>{children(value, (next) => setValue((previous) => ({ ...previous, ...next })))}</>;
}
