import { expect } from "storybook/test";

/**
 * SC 4.1.2: `pointer-events-none` blocks the mouse and nothing else, so the contract is the native
 * `disabled` attribute. The focus check is what an `aria-disabled` look-alike would fail.
 */
export async function expectGenuinelyDisabled(control: HTMLElement) {
	await expect(control).toBeDisabled();
	const before = document.activeElement;
	control.focus();
	await expect(document.activeElement).toBe(before);
}

/**
 * The same, for a Base UI `<span role="switch">`: no native `disabled`, so `aria-disabled` plus
 * removal from the tab order is the whole contract. Neither says the press is ignored.
 */
export async function expectUnavailable(control: HTMLElement) {
	await expect(control).toHaveAttribute("aria-disabled", "true");
	await expect(control).toHaveAttribute("tabindex", "-1");
}
