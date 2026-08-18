import { type ReactNode, useState } from "react";

/**
 * Closes the loop for a controlled component in a story. A frozen value paired with `fn()` leaves
 * every control on the story inert while nothing looks broken — a spy is a perfectly good `onChange`,
 * and it is the pairing with an unchanging value that is wrong.
 *
 * `fn()` alone stays right for a callback with no paired value (`onDelete`, `onSubmit`). Spread
 * `args` through this so Controls still edits the initial value.
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
