import { expect } from "storybook/test";

/**
 * SC 4.1.2: dimming a control with `pointer-events-none` blocks the mouse and nothing else — it
 * stays in the tab order and is still announced as available, so the real contract is the native
 * `disabled` attribute. The focus check is what an `aria-disabled` look-alike would fail.
 */
export async function expectGenuinelyDisabled(control: HTMLElement) {
	await expect(control).toBeDisabled();
	const before = document.activeElement;
	control.focus();
	await expect(document.activeElement).toBe(before);
}

/**
 * The same claim for a non-form control: Base UI draws a switch as a `<span role="switch">`, which
 * has no native `disabled`, so `aria-disabled` plus removal from the tab order is the whole
 * contract. `data-disabled` is only the kit's styling hook and asserts nothing. Neither attribute
 * says the press is ignored, so callers assert that separately.
 */
export async function expectUnavailable(control: HTMLElement) {
	await expect(control).toHaveAttribute("aria-disabled", "true");
	await expect(control).toHaveAttribute("tabindex", "-1");
}
