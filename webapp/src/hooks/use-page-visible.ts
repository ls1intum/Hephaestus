import { useSyncExternalStore } from "react";

const subscribe = (onStoreChange: () => void) => {
	document.addEventListener("visibilitychange", onStoreChange);
	return () => document.removeEventListener("visibilitychange", onStoreChange);
};

/**
 * Whether the page is visible: not a background tab, a minimised window or a locked screen. Read
 * through `useSyncExternalStore`, as `useIsMobile` reads its media query, so a page that opens hidden
 * never acts as visible for one render.
 */
export function usePageVisible(): boolean {
	return useSyncExternalStore(
		subscribe,
		() => document.visibilityState === "visible",
		() => true,
	);
}
