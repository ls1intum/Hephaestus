/**
 * ARIA naming props for an element whose role requires an accessible name, shaped so that supplying
 * neither is a compile error. A popup `listbox` renders into a portal, where nothing names it
 * implicitly and an omission surfaces only in a scan that catches the popup open.
 *
 * Prefer `aria-labelledby` pointed at the visible label, so the accessible name cannot drift from
 * the words on screen (WCAG 2.5.3 Label in Name).
 */
export type AccessibleNameProps =
	| { "aria-label": string; "aria-labelledby"?: never }
	| { "aria-label"?: never; "aria-labelledby": string };
