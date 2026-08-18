import { expect, waitFor } from "storybook/test";

/**
 * The six primitives in this kit that hang their popup off a Base UI `Positioner`, by the
 * `data-slot` each one stamps. Spelled out rather than matched loosely, so an open dialog — which
 * is portalled but not positioned — is not mistaken for one.
 */
export const POSITIONED_POPUPS = [
	"popover-content",
	"hover-card-content",
	"tooltip-content",
	"dropdown-menu-content",
	"select-content",
	"combobox-content",
]
	.map((slot) => `[data-slot='${slot}']`)
	.join(", ");

/**
 * Every enter animation currently running on `element` or on anything it sits inside.
 *
 * The ancestors are the point: a popup fades *itself* in, so the animation belongs to the popup
 * while the thing being asserted on is usually a `<dt>` or a `<p>` several levels down, which has no
 * animation of its own and would otherwise look settled the moment it mounts.
 */
function enteringAnimationsOf(element: Element): Animation[] {
	return document.getAnimations().filter((animation) => {
		const target = (animation.effect as KeyframeEffect | null)?.target;
		return target instanceof Element && target.contains(element);
	});
}

/**
 * `toBeVisible()` on something inside a just-opened overlay, once the overlay has actually arrived.
 *
 * Base UI stamps `data-starting-style` on a popup for the frame it mounts in and clears it on the
 * next one; the enter styles it selects hold the popup at `opacity: 0` and `scale: .95`. Assert
 * inside that frame and `toBeVisible()` reads the transparent ancestor and fails — which is why the
 * weaker `toBeInTheDocument()` was reached for instead. It is not animation *duration*: reduced
 * motion is on in this suite and forcing `1ms !important` durations does not help, because the
 * attribute is a lifecycle marker rather than a timing one. Waiting for the attribute to clear is,
 * so this waits for that and then for whatever transition it handed off to.
 *
 * For the popup element itself rather than its contents, see `settledPopup`.
 */
export async function expectSettledVisible(element: HTMLElement): Promise<void> {
	await waitFor(() => {
		expect(
			element.closest("[data-starting-style]"),
			"Still inside the overlay's starting-style frame, where everything in it is transparent.",
		).toBeNull();
	});
	await Promise.all(
		enteringAnimationsOf(element).map((animation) =>
			// `finished` *rejects* with an AbortError when the animation is cancelled rather than run
			// to its end, which is ordinary here: a popup that re-positions while opening replaces its
			// own enter animation. A cancelled animation no longer applies, which is all this needs to
			// know, so the rejection is an outcome rather than a failure. The `waitFor` below is what
			// covers the replacement it was swapped for.
			animation.finished.catch(() => undefined),
		),
	);
	await waitFor(() => expect(element).toBeVisible());
}

/**
 * The one open positioned popup, once it has landed — for the measuring assertions, which need the
 * box rather than a query hit, and would read a mid-flight `scale(.95)` as a popup that fits.
 */
export async function settledPopup(): Promise<HTMLElement> {
	const popup = await waitFor(() => {
		const open = document.querySelector<HTMLElement>(POSITIONED_POPUPS);
		if (open == null) {
			throw new Error("No overlay is open, so measuring the page would prove nothing.");
		}
		return open;
	});
	await expectSettledVisible(popup);
	return popup;
}
