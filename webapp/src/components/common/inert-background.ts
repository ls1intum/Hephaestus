/**
 * A ref callback for a modal's popup that makes everything behind it `inert` while it is mounted:
 * every child of `<body>` but the one the popup is portalled into. Base UI hides the background
 * from a screen reader (`aria-hidden`) and traps focus, but a pointer can still reach a control
 * behind the backdrop's blur, such as a table row that opens on a click. `inert` takes the
 * background out of pointer, keyboard and accessibility reach at once. The marks are lifted when the
 * popup unmounts, before Base UI returns focus to the control that opened it.
 */
export function inertBackground(popup: HTMLElement | null): (() => void) | undefined {
	if (popup === null) {
		return undefined;
	}
	const marked = [...document.body.children].filter(
		(child): child is HTMLElement =>
			child instanceof HTMLElement &&
			!child.contains(popup) &&
			!child.inert &&
			child.tagName !== "SCRIPT",
	);
	for (const element of marked) {
		element.inert = true;
	}
	return () => {
		for (const element of marked) {
			element.inert = false;
		}
	};
}
