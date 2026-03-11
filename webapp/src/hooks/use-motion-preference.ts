import { useSyncExternalStore } from "react";
import { useAccessibilityStore } from "@/stores/accessibility-store";
import environment from "@/environment";

const reducedMotionQuery =
	typeof window !== "undefined" ? window.matchMedia("(prefers-reduced-motion: reduce)") : null;

function subscribe(callback: () => void) {
	reducedMotionQuery?.addEventListener("change", callback);
	return () => reducedMotionQuery?.removeEventListener("change", callback);
}

function getSnapshot() {
	return reducedMotionQuery?.matches ?? false;
}

function getServerSnapshot() {
	return false;
}

/**
 * Returns true if motion should be reduced, respecting both system settings
 * and the user's explicit override in Hephaestus.
 *
 * In DEV mode, defaults to full motion (false) even if the system setting
 * prefers reduced motion, unless the user manually overrides it to 'reduced'.
 */
export function useMotionPreference() {
	const motionSetting = useAccessibilityStore((state) => state.motion);
	const prefersReducedMotion = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

	if (motionSetting === "reduced") return true;
	if (motionSetting === "full") return false;

	// 'system' or fallback
	// In DEV, we default to full motion to show off the aesthetics
	if (environment.version === "DEV") return false;

	return prefersReducedMotion;
}
