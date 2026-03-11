import { useSyncExternalStore } from "react";
import { useMotionPreference } from "./use-motion-preference";

/**
 * Shared animation clock that consolidates all requestAnimationFrame loops
 * into a single global loop. Returns time in seconds (performance.now() * 0.001).
 *
 * Respects `useMotionPreference()`: returns a static `0` when reduced motion
 * is preferred, and the rAF loop is not started at all.
 *
 * Uses `useSyncExternalStore` for tear-free reads across concurrent renders.
 */

let currentTime = 0;
let rafId: number | null = null;
let subscriberCount = 0;
const listeners = new Set<() => void>();

function tick(now: number) {
	currentTime = now * 0.001;
	for (const listener of listeners) {
		listener();
	}
	rafId = requestAnimationFrame(tick);
}

function startLoop() {
	if (rafId === null) {
		rafId = requestAnimationFrame(tick);
	}
}

function stopLoop() {
	if (rafId !== null) {
		cancelAnimationFrame(rafId);
		rafId = null;
	}
}

function subscribe(callback: () => void) {
	listeners.add(callback);
	subscriberCount++;
	if (subscriberCount === 1) {
		startLoop();
	}
	return () => {
		listeners.delete(callback);
		subscriberCount--;
		if (subscriberCount === 0) {
			stopLoop();
		}
	};
}

function getSnapshot() {
	return currentTime;
}

function getServerSnapshot() {
	return 0;
}

const ZERO_SUBSCRIBE = (_cb: () => void) => () => {};
const ZERO_SNAPSHOT = () => 0;

/**
 * Returns the current animation time in seconds, synchronized across all consumers.
 *
 * A single shared `requestAnimationFrame` loop drives all subscribed components.
 * When `useMotionPreference()` returns true or `enabled` is false,
 * returns `0` and no loop runs.
 */
export function useAnimationTime(enabled = true): number {
	const reducedMotion = useMotionPreference();

	const shouldAnimate = enabled && !reducedMotion;

	return useSyncExternalStore(
		shouldAnimate ? subscribe : ZERO_SUBSCRIBE,
		shouldAnimate ? getSnapshot : ZERO_SNAPSHOT,
		getServerSnapshot,
	);
}
