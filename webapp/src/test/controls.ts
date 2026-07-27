import { expect } from "storybook/test";

/**
 * A control the user genuinely cannot operate, as opposed to one that merely looks unavailable.
 *
 * The distinction is WCAG 2.2 SC 4.1.2 Name, Role, Value. Dimming an anchor with
 * `pointer-events-none` blocks the mouse and nothing else: the element stays in the tab order and
 * assistive tech still reports it as an available control, so a keyboard or screen-reader user is
 * invited to activate a "Previous" that has no previous page. A native `disabled` button is removed
 * from the tab order and announced as unavailable, which is why the pagers use one.
 *
 * Two assertions, because neither alone catches the failure: `disabled` is the attribute assistive
 * tech reads, and the focus check is what an `aria-disabled` look-alike would fail.
 */
export async function expectGenuinelyDisabled(control: HTMLElement) {
	await expect(control).toBeDisabled();
	const before = document.activeElement;
	control.focus();
	await expect(document.activeElement).toBe(before);
}

/**
 * The same claim for a control that is not a form element, where the platform's `disabled` attribute
 * is not available at all: Base UI draws a switch as a `<span role="switch">`, so `aria-disabled`
 * plus removal from the tab order is the whole of the contract.
 *
 * `data-disabled` is *not* that contract — it is the styling hook the kit uses to grey the control,
 * and a switch carrying it while still announced as available and still reachable by Tab is the same
 * SC 4.1.2 failure the dimmed anchor above is. Neither attribute says the press is ignored, so
 * callers state that separately, against whatever operating it would have changed.
 */
export async function expectUnavailable(control: HTMLElement) {
	await expect(control).toHaveAttribute("aria-disabled", "true");
	await expect(control).toHaveAttribute("tabindex", "-1");
}
