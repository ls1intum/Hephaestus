import * as React from "react";

const MOBILE_BREAKPOINT = 768;
const MOBILE_QUERY = `(max-width: ${MOBILE_BREAKPOINT - 1}px)`;

const subscribe = (onStoreChange: () => void) => {
	const query = window.matchMedia(MOBILE_QUERY);
	query.addEventListener("change", onStoreChange);
	return () => query.removeEventListener("change", onStoreChange);
};

/**
 * Reads the viewport through `useSyncExternalStore` rather than mirroring it into state from an
 * effect. The state-and-effect version reported desktop on the first render and corrected itself on
 * the second, so a sidebar mounted on a phone rendered its desktop layout once before switching.
 */
export function useIsMobile() {
	return React.useSyncExternalStore(
		subscribe,
		() => window.matchMedia(MOBILE_QUERY).matches,
		() => false,
	);
}
