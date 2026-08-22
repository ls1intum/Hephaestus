/**
 * The ARIA naming props for an element whose role requires an accessible name of its own, written so
 * that supplying neither is a type error.
 *
 * `listbox` is the case this exists for. A popup listbox renders into a portal, so no ancestor can
 * name it, and the primitives that own the role know the item labels but not the trigger or the
 * field label beside it — nothing supplies a name implicitly. An omission is invisible on screen and
 * surfaces only in an accessibility scan that happens to catch the popup open, so the call site is
 * the last place the mistake is cheap.
 *
 * A union rather than two optional props: optional pairs can only be checked at runtime, long after
 * the omission shipped. `never` on the unused half keeps both from being set at once, where a reader
 * of the call site cannot tell which name wins.
 *
 * Prefer `aria-labelledby` pointed at the visible label element. One source of truth means the
 * accessible name cannot drift from the words on screen, which is its own failure (WCAG 2.5.3 Label
 * in Name). A literal `aria-label` is for controls with no visible label to point at.
 */
export type AccessibleNameProps =
	| { "aria-label": string; "aria-labelledby"?: never }
	| { "aria-label"?: never; "aria-labelledby": string };
