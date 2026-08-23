import { useSyncExternalStore } from "react";

const MOBILE_BREAKPOINT = 768;
const MOBILE_QUERY = `(max-width: ${MOBILE_BREAKPOINT - 1}px)`;

const subscribe = (onStoreChange: () => void) => {
	const query = window.matchMedia(MOBILE_QUERY);
	query.addEventListener("change", onStoreChange);
	return () => query.removeEventListener("change", onStoreChange);
};

/**
 * Read through `useSyncExternalStore`, not mirrored into state from an effect: the effect version
 * answers desktop on the first render and corrects itself on the second, so a sidebar mounted on a
 * phone paints its desktop layout once before switching.
 */
export function useIsMobile() {
	return useSyncExternalStore(
		subscribe,
		() => window.matchMedia(MOBILE_QUERY).matches,
		() => false,
	);
}
