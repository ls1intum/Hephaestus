import { expect } from "storybook/test";

/**
 * Assertions for WCAG 2.2 SC 1.4.10 (Reflow) and SC 2.5.8 (Target Size, Minimum), written to run in
 * the Storybook browser tier.
 *
 * SC 1.4.10 requires content to reflow to a 320 CSS px viewport — equivalently 1280 px at 400 %
 * zoom — without requiring scrolling in two dimensions. Data tables are the standard's own listed
 * exception: they may scroll horizontally, but the page around them may not, so "a table is wide"
 * and "the page is wide" are two different findings and only the second is a failure.
 *
 * Stories using these must set `parameters.viewport.defaultViewport = "reflow"`, which really does
 * resize the browser in this runner. {@link expectPageReflows} asserts that it took effect before
 * measuring anything, so a story that forgets the parameter fails loudly rather than passing at
 * desktop width and proving nothing.
 */

/** Half a device pixel of slack, so a fractional layout position never flakes the suite. */
const EPSILON = 1;

/** The WCAG 2.2 SC 1.4.10 reflow width, in CSS px. */
export const REFLOW_WIDTH = 320;

/**
 * The page reflows to 320 px: no horizontal scrollbar on the document.
 *
 * The viewport guard is not ceremony — without it this function silently measures a 1280 px window
 * and passes for any layout at all.
 */
export async function expectPageReflows() {
	await expect(window.innerWidth).toBeLessThanOrEqual(REFLOW_WIDTH + EPSILON);
	await expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(
		document.documentElement.clientWidth + EPSILON,
	);
}

/**
 * Every table under `root` confines its horizontal overflow to its own scroll container.
 *
 * A wide table is allowed by SC 1.4.10; a wide table that drags the page sideways with it is not.
 * The container must both *be* a scroller and *fit* the viewport, or the overflow escapes it.
 */
export async function expectTablesScrollInPlace(root: HTMLElement | Document = document) {
	const containers = root.querySelectorAll<HTMLElement>('[data-slot="table-container"]');
	await expect(containers.length).toBeGreaterThan(0);
	for (const container of containers) {
		await expect(["auto", "scroll"]).toContain(getComputedStyle(container).overflowX);
		await expect(container.getBoundingClientRect().width).toBeLessThanOrEqual(
			window.innerWidth + EPSILON,
		);
	}
}

/** WCAG 2.2 SC 2.5.8 Target Size (Minimum): an interactive target is at least 24 x 24 CSS px. */
export async function expectTargetSize(control: HTMLElement) {
	const rect = control.getBoundingClientRect();
	await expect(rect.width).toBeGreaterThanOrEqual(24);
	await expect(rect.height).toBeGreaterThanOrEqual(24);
}

/**
 * SC 2.5.8's *Spacing* exception, for targets that are deliberately smaller than 24 x 24.
 *
 * The criterion is met by an undersized target when a 24 px diameter circle centred on it does not
 * intersect the circle of any other target. Switches and checkboxes are the usual case: shadcn (and
 * the platform conventions it follows) draws them well under 24 px tall, and it is the generous
 * vertical rhythm around them that makes them conformant. Asserting the spacing is the honest test —
 * asserting the size would only prove the component is not the shape it was designed to be.
 */
export async function expectTargetSpacing(controls: HTMLElement[]) {
	await expect(controls.length).toBeGreaterThan(0);
	const centres = controls.map((control) => {
		const rect = control.getBoundingClientRect();
		return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
	});
	for (let i = 0; i < centres.length; i++) {
		for (let j = i + 1; j < centres.length; j++) {
			const dx = centres[i].x - centres[j].x;
			const dy = centres[i].y - centres[j].y;
			// Two 24 px circles do not intersect when their centres are at least 24 px apart.
			await expect(Math.hypot(dx, dy)).toBeGreaterThanOrEqual(24);
		}
	}
}

/** A control is fully inside the viewport. Says nothing about its size. */
export async function expectWithinViewport(control: HTMLElement) {
	const rect = control.getBoundingClientRect();
	await expect(rect.top).toBeGreaterThanOrEqual(-EPSILON);
	await expect(rect.bottom).toBeLessThanOrEqual(window.innerHeight + EPSILON);
	await expect(rect.left).toBeGreaterThanOrEqual(-EPSILON);
	await expect(rect.right).toBeLessThanOrEqual(window.innerWidth + EPSILON);
}

/**
 * A control is fully inside the viewport *and* meets the minimum target size.
 *
 * Only for controls that are not inside a horizontally scrolling region — a table row's action is
 * legitimately off to the right until the table is scrolled, so use {@link expectTargetSize} there.
 */
export async function expectControlOnScreen(control: HTMLElement) {
	await expectWithinViewport(control);
	await expectTargetSize(control);
}

/** The popup element of the currently open dialog (or alert dialog). */
export function openDialogPopup(): HTMLElement {
	const popup = document.querySelector<HTMLElement>(
		'[data-slot="dialog-content"], [data-slot="alert-dialog-content"]',
	);
	if (popup == null) {
		throw new Error("No open dialog: expected a [data-slot=dialog-content] popup in the document.");
	}
	return popup;
}

/**
 * The popup is bounded by the viewport and fully inside it.
 *
 * `DialogContent`/`AlertDialogContent` are `position: fixed` and centred with `-translate-y-1/2`, so
 * a popup taller than the viewport hangs off *both* edges at once and neither end can be scrolled
 * back into view — the title and the submit button simply become unreachable. That is the defect
 * this pins: before the height bound, the tall forms rendered with their top edge ~300 px above the
 * viewport.
 */
export async function expectDialogFitsViewport(popup: HTMLElement = openDialogPopup()) {
	// A real cap, not `none`: this is what stops the popup growing past the viewport in the first
	// place, and it is the single declaration a future refactor is most likely to drop.
	await expect(getComputedStyle(popup).maxHeight).not.toBe("none");

	// `offsetWidth`/`offsetHeight` are the *layout* box. `getBoundingClientRect` is not, and the
	// popup's `zoom-in-95` enter animation may still be mid-flight when a `play` runs — a 0.95 scale
	// shrinks the measured rect by 5 % and would let a popup that is slightly too big pass. Layout
	// size is transform-free and therefore the assertion that actually holds.
	await expect(popup.offsetHeight).toBeLessThanOrEqual(window.innerHeight);
	await expect(popup.offsetWidth).toBeLessThanOrEqual(window.innerWidth);

	const rect = popup.getBoundingClientRect();
	await expect(rect.top).toBeGreaterThanOrEqual(-EPSILON);
	await expect(rect.bottom).toBeLessThanOrEqual(window.innerHeight + EPSILON);
	await expect(rect.left).toBeGreaterThanOrEqual(-EPSILON);
	await expect(rect.right).toBeLessThanOrEqual(window.innerWidth + EPSILON);
}

/**
 * The dialog scrolls its own body rather than the page, and the header and footer stay put.
 *
 * `overflow-hidden` on the popup is what proves the popup itself is not the scroller; a scrollable
 * `[data-slot=dialog-body]` is what proves the overflow went somewhere reachable.
 */
export async function expectDialogBodyScrolls(popup: HTMLElement = openDialogPopup()) {
	const body = popup.querySelector<HTMLElement>('[data-slot="dialog-body"]');
	await expect(body).not.toBeNull();
	if (body == null) return;

	await expect(getComputedStyle(popup).overflowY).toBe("hidden");
	await expect(["auto", "scroll"]).toContain(getComputedStyle(body).overflowY);
	// The body absorbed the overflow instead of the popup growing.
	await expect(body.scrollHeight).toBeGreaterThan(body.clientHeight);
}
