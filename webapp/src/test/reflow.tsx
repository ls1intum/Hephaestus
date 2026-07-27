import { expect } from "storybook/test";

/**
 * Assertions for WCAG 2.2 SC 1.4.10 (Reflow) and SC 2.5.8 (Target Size), for the Storybook browser
 * tier. Callers must set `parameters.viewport.defaultViewport = "reflow"`.
 *
 * Every assertion here that reads the viewport checks the viewport is narrow *first*, via
 * {@link expectReflowViewport}. That guard belongs in the helpers rather than in each story: a story
 * that measures a dialog against a 1280 px window is not checking reflow at all, it is passing
 * vacuously, and "remember to also call `expectPageReflows`" is exactly the kind of instruction a
 * caller drops. Drop `defaultViewport: "reflow"` from a story that calls these helpers and its play
 * function fails.
 */

/** Half a device pixel of slack, so a fractional layout position never flakes the suite. */
const EPSILON = 1;

/** The WCAG 2.2 SC 1.4.10 reflow width, in CSS px. */
export const REFLOW_WIDTH = 320;

/**
 * The story is actually running at the reflow width. Not an accessibility assertion in itself — it
 * is the precondition that makes the others mean anything, so they all open with it.
 */
export async function expectReflowViewport() {
	await expect(
		window.innerWidth,
		`Expected the reflow viewport (<= ${REFLOW_WIDTH} px) but the window is ${window.innerWidth} px wide. Set parameters.viewport.defaultViewport = "reflow" on this story.`,
	).toBeLessThanOrEqual(REFLOW_WIDTH + EPSILON);
}

export async function expectPageReflows() {
	await expectReflowViewport();
	await expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(
		document.documentElement.clientWidth + EPSILON,
	);
}

/**
 * The nearest ancestor that scrolls `element` sideways. Found by behaviour rather than by a kit
 * class or `data-slot`, so a renamed container in the UI kit cannot silently turn the assertions
 * that use it into no-ops.
 */
export function horizontalScrollParentOf(element: HTMLElement): HTMLElement {
	for (let node = element.parentElement; node != null; node = node.parentElement) {
		if (["auto", "scroll"].includes(getComputedStyle(node).overflowX)) {
			return node;
		}
	}
	throw new Error(
		"A table wider than the viewport has no horizontally scrollable ancestor, so its right-hand columns cannot be reached.",
	);
}

/**
 * A wide table is SC 1.4.10's own listed exception; a wide table that drags the page sideways is
 * not, and one whose overflow is clipped is worse — the columns become unreachable.
 *
 * `expectOverflow` states which of the two situations the caller is in. Without it a table that
 * happens to fit passes the "and it really does scroll" half by never reaching it, which is the
 * vacuous pass this argument exists to make impossible: a story that means to prove the exception
 * says so, and fails if its table ever stops overflowing.
 */
export async function expectTablesScrollInPlace(
	root: HTMLElement | Document = document,
	{ expectOverflow = false }: { expectOverflow?: boolean } = {},
) {
	await expectReflowViewport();
	const tables = root.querySelectorAll<HTMLElement>("table");
	await expect(tables.length).toBeGreaterThan(0);
	for (const table of tables) {
		const scroller = horizontalScrollParentOf(table);
		await expect(scroller.getBoundingClientRect().width).toBeLessThanOrEqual(
			window.innerWidth + EPSILON,
		);
		const overflows = table.scrollWidth > scroller.clientWidth + EPSILON;
		if (expectOverflow) {
			await expect(
				overflows,
				"Expected this table to be wider than its scroller, so that the scrolling assertion below means something.",
			).toBe(true);
		}
		if (overflows) {
			scroller.scrollLeft = scroller.scrollWidth;
			await expect(scroller.scrollLeft).toBeGreaterThan(0);
		}
	}
}

/**
 * WCAG 2.2 SC 2.5.8 Target Size (Minimum): an interactive target is at least 24 x 24 CSS px.
 *
 * No {@link expectReflowViewport} guard, unlike the helpers above: 24 px is 24 px at any width, so
 * this assertion is not the kind that passes vacuously on a desktop-sized window.
 */
export async function expectTargetSize(control: HTMLElement) {
	const rect = control.getBoundingClientRect();
	await expect(rect.width).toBeGreaterThanOrEqual(24);
	await expect(rect.height).toBeGreaterThanOrEqual(24);
}

/**
 * SC 2.5.8's *Spacing* exception: an undersized target conforms when a 24 px circle centred on it
 * intersects no other target's circle. Switches and checkboxes are drawn well under 24 px by
 * convention, so their spacing is what makes them conformant.
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
	await expectReflowViewport();
	const rect = control.getBoundingClientRect();
	await expect(rect.top).toBeGreaterThanOrEqual(-EPSILON);
	await expect(rect.bottom).toBeLessThanOrEqual(window.innerHeight + EPSILON);
	await expect(rect.left).toBeGreaterThanOrEqual(-EPSILON);
	await expect(rect.right).toBeLessThanOrEqual(window.innerWidth + EPSILON);
}

/**
 * Not for controls inside a horizontally scrolling region — a table row's action is legitimately
 * off to the right until the table is scrolled, so use {@link expectTargetSize} there.
 */
export async function expectControlOnScreen(control: HTMLElement) {
	await expectWithinViewport(control);
	await expectTargetSize(control);
}

/**
 * The dialog the user is currently in, found the way assistive tech finds it. The last match wins:
 * layers portal in open order, so a confirm dialog raised from inside a sheet comes after it.
 */
export function openDialogPopup(): HTMLElement {
	const popups = document.querySelectorAll<HTMLElement>('[role="dialog"], [role="alertdialog"]');
	const popup = popups[popups.length - 1];
	if (popup == null) {
		throw new Error("No open dialog: expected an element with role dialog or alertdialog.");
	}
	return popup;
}

/**
 * A `position: fixed` popup centred with `-translate-y-1/2` hangs off *both* edges once it outgrows
 * the viewport, and the page cannot scroll a fixed element back into view — a tall form's title
 * lands ~300 px above the top edge and neither it nor the submit button can be reached.
 */
export async function expectDialogFitsViewport(popup: HTMLElement = openDialogPopup()) {
	await expectReflowViewport();
	// The layout box, not `getBoundingClientRect`: the `zoom-in-95` enter animation may still be
	// mid-flight, and its 0.95 scale would let a slightly-too-big popup pass.
	await expect(popup.offsetHeight).toBeLessThanOrEqual(window.innerHeight);
	await expect(popup.offsetWidth).toBeLessThanOrEqual(window.innerWidth);

	await expectWithinViewport(popup);
}

/**
 * What the `DialogBody` divergence buys, and the reason it is worth carrying: with the whole popup
 * as the scroller, reaching the bottom of a long form scrolls the title out of sight.
 */
export async function expectDialogBodyScrolls(popup: HTMLElement = openDialogPopup()) {
	await expectReflowViewport();
	const heading = popup.querySelector<HTMLElement>('h2, [role="heading"]');
	if (heading == null) {
		throw new Error("A dialog with no heading has nothing to keep pinned while its body scrolls.");
	}

	const scrollers = [popup, ...popup.querySelectorAll<HTMLElement>("*")].filter(
		(node) =>
			node.scrollHeight > node.clientHeight + EPSILON &&
			["auto", "scroll"].includes(getComputedStyle(node).overflowY),
	);
	await expect(scrollers.length).toBe(1);

	const before = heading.getBoundingClientRect().top;
	scrollers[0].scrollTop = scrollers[0].scrollHeight;
	await expect(scrollers[0].scrollTop).toBeGreaterThan(0);
	await expect(Math.abs(heading.getBoundingClientRect().top - before)).toBeLessThanOrEqual(EPSILON);
	await expectWithinViewport(heading);
}
