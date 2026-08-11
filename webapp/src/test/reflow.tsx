import { expect, waitFor } from "storybook/test";

const LAYOUT_SLACK_PX = 1;
const MIN_TARGET_PX = 24;

export async function expectNoPageOverflow() {
	await expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(
		document.documentElement.clientWidth + LAYOUT_SLACK_PX,
	);
}

/**
 * Overlay popups are positioned against the viewport rather than the document, so that an open one
 * cannot widen the page (SC 1.4.10). The price is that nothing moves them for free: this asserts the
 * popup still holds its offset from the trigger after `scroll` has moved the trigger.
 */
export async function expectOverlayFollowsTrigger(
	trigger: HTMLElement,
	popup: HTMLElement,
	scroll: () => void,
) {
	const offsetOf = () => {
		const triggerRect = trigger.getBoundingClientRect();
		const popupRect = popup.getBoundingClientRect();
		return { x: popupRect.left - triggerRect.left, y: popupRect.top - triggerRect.top };
	};

	const before = offsetOf();
	const triggerTopBefore = trigger.getBoundingClientRect().top;
	scroll();
	await waitFor(() => {
		expect(
			Math.abs(trigger.getBoundingClientRect().top - triggerTopBefore),
			"The scroll callback did not move the trigger, so the assertion below would prove nothing.",
		).toBeGreaterThan(LAYOUT_SLACK_PX);
	});

	// Base UI repositions on the next frame, not synchronously with the scroll event.
	await waitFor(() => {
		const after = offsetOf();
		expect(
			Math.abs(after.x - before.x),
			"The popup drifted horizontally off its trigger.",
		).toBeLessThanOrEqual(LAYOUT_SLACK_PX);
		expect(
			Math.abs(after.y - before.y),
			"The popup drifted vertically off its trigger.",
		).toBeLessThanOrEqual(LAYOUT_SLACK_PX);
	});
}

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
 * A wide table is SC 1.4.10's own exception only while it scrolls in place, rather than dragging the
 * page sideways or clipping its right-hand columns away.
 */
export async function expectTablesScrollInPlace(
	root: HTMLElement | Document = document,
	{ expectOverflow = false }: { expectOverflow?: boolean } = {},
) {
	const tables = root.querySelectorAll<HTMLElement>("table");
	await expect(tables.length).toBeGreaterThan(0);
	for (const table of tables) {
		const scroller = horizontalScrollParentOf(table);
		await expect(scroller.getBoundingClientRect().width).toBeLessThanOrEqual(
			window.innerWidth + LAYOUT_SLACK_PX,
		);
		const overflows = table.scrollWidth > scroller.clientWidth + LAYOUT_SLACK_PX;
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

export async function expectTargetSize(control: HTMLElement) {
	const rect = control.getBoundingClientRect();
	await expect(rect.width).toBeGreaterThanOrEqual(MIN_TARGET_PX);
	await expect(rect.height).toBeGreaterThanOrEqual(MIN_TARGET_PX);
}

/** SC 2.5.8's *Spacing* exception: an undersized target passes when its 24 px circle hits no other. */
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
			await expect(Math.hypot(dx, dy)).toBeGreaterThanOrEqual(MIN_TARGET_PX);
		}
	}
}

export async function expectWithinViewport(control: HTMLElement) {
	const rect = control.getBoundingClientRect();
	await expect(rect.top).toBeGreaterThanOrEqual(-LAYOUT_SLACK_PX);
	await expect(rect.bottom).toBeLessThanOrEqual(window.innerHeight + LAYOUT_SLACK_PX);
	await expect(rect.left).toBeGreaterThanOrEqual(-LAYOUT_SLACK_PX);
	await expect(rect.right).toBeLessThanOrEqual(window.innerWidth + LAYOUT_SLACK_PX);
}

/** Not for a control inside a horizontal scroller — it is legitimately off-screen until scrolled. */
export async function expectControlOnScreen(control: HTMLElement) {
	await expectWithinViewport(control);
	await expectTargetSize(control);
}

/** The last match wins: layers portal in open order, so a confirm raised from a sheet comes after it. */
export function openDialogPopup(): HTMLElement {
	const popups = document.querySelectorAll<HTMLElement>('[role="dialog"], [role="alertdialog"]');
	const popup = popups[popups.length - 1];
	if (popup == null) {
		throw new Error("No open dialog: expected an element with role dialog or alertdialog.");
	}
	return popup;
}

/** A centred `fixed` popup hangs off *both* edges once it outgrows the viewport, and cannot be scrolled back. */
export async function expectDialogFitsViewport(popup: HTMLElement = openDialogPopup()) {
	// The layout box, not `getBoundingClientRect`: the `zoom-in-95` enter animation may still be
	// mid-flight, and its 0.95 scale would let a slightly-too-big popup pass.
	await expect(popup.offsetHeight).toBeLessThanOrEqual(window.innerHeight);
	await expect(popup.offsetWidth).toBeLessThanOrEqual(window.innerWidth);

	await expectWithinViewport(popup);
}

/** Exactly one scroller, so reaching the bottom of a long form cannot scroll the title out of sight. */
export async function expectDialogBodyScrolls(popup: HTMLElement = openDialogPopup()) {
	const heading = popup.querySelector<HTMLElement>('h2, [role="heading"]');
	if (heading == null) {
		throw new Error("A dialog with no heading has nothing to keep pinned while its body scrolls.");
	}

	const scrollers = [popup, ...popup.querySelectorAll<HTMLElement>("*")].filter(
		(node) =>
			node.scrollHeight > node.clientHeight + LAYOUT_SLACK_PX &&
			["auto", "scroll"].includes(getComputedStyle(node).overflowY),
	);
	await expect(scrollers.length).toBe(1);

	const before = heading.getBoundingClientRect().top;
	scrollers[0].scrollTop = scrollers[0].scrollHeight;
	await expect(scrollers[0].scrollTop).toBeGreaterThan(0);
	await expect(Math.abs(heading.getBoundingClientRect().top - before)).toBeLessThanOrEqual(
		LAYOUT_SLACK_PX,
	);
	await expectWithinViewport(heading);
}
