/**
 * Shared DOM utilities for consistent viewport calculations.
 */

/** Threshold (in characters) for auto-opening the artifact overlay */
export const AUTO_OPEN_THRESHOLD = 200;

/** Get a DOMRect centered in the current viewport */
export function getCenteredRect(size = 100): DOMRect {
	const vv = window.visualViewport ?? {
		offsetLeft: 0,
		offsetTop: 0,
		width: window.innerWidth,
		height: window.innerHeight,
	};
	const cx = vv.offsetLeft + vv.width / 2;
	const cy = vv.offsetTop + vv.height / 2;
	return new DOMRect(cx - size / 2, cy - size / 2, size, size);
}
