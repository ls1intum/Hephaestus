import { expect } from "storybook/test";
import type { Canvas } from "@/test/canvas";

/**
 * A closed Base UI select shows the *label* for its value, looked up from the options.
 *
 * When that lookup misses, the trigger falls back to printing the raw value — so asserting the label
 * is what tells a review form that offers "Pull or merge requests" apart from one that asks an admin
 * to authorise "scm.pull_request", which is not a decision anyone outside the schema can check.
 * Worth stating because the fallback is silent: the control still renders, and still reads as filled.
 */
export async function expectClosedSelectShows(
	canvas: Canvas,
	name: RegExp | string,
	label: string,
) {
	await expect(canvas.getByRole("combobox", { name })).toHaveTextContent(label);
}

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
