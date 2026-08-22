import { useEffect, useState } from "react";

/**
 * Below 1 s a reader perceives the result as immediate and
 * [needs no feedback at all](https://www.nngroup.com/articles/response-times-3-important-limits/);
 * a loading state that appears and vanishes inside that window is a flash, which reads as a fault
 * rather than as progress. Once one is shown it must stay long enough to be read.
 *
 * The router already applies the same idea to route transitions (`defaultPendingMs` 1000,
 * `defaultPendingMinMs` 500); this is the equivalent for a query-driven region inside a page.
 */
export function useSpinDelay(
	pending: boolean,
	{ delay = 500, minDuration = 200 }: { delay?: number; minDuration?: number } = {},
): boolean {
	const [visible, setVisible] = useState(false);
	const [shownAt, setShownAt] = useState<number | null>(null);

	useEffect(() => {
		if (pending) {
			const timer = setTimeout(() => {
				setShownAt(performance.now());
				setVisible(true);
			}, delay);
			return () => clearTimeout(timer);
		}
		if (!visible) {
			return;
		}
		const remaining = shownAt === null ? 0 : minDuration - (performance.now() - shownAt);
		if (remaining <= 0) {
			setVisible(false);
			setShownAt(null);
			return;
		}
		const timer = setTimeout(() => {
			setVisible(false);
			setShownAt(null);
		}, remaining);
		return () => clearTimeout(timer);
	}, [pending, visible, shownAt, delay, minDuration]);

	return visible;
}
