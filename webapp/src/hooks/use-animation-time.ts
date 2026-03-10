import { useSyncExternalStore } from "react";

/**
 * Shared animation clock that consolidates all requestAnimationFrame loops
 * into a single global loop. Returns time in seconds (performance.now() * 0.001).
 *
 * Respects `prefers-reduced-motion`: returns a static `0` when reduced motion
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

// Module-level shared reduced-motion state that reacts to runtime changes
const reducedMotionQuery =
	typeof window !== "undefined" ? window.matchMedia("(prefers-reduced-motion: reduce)") : null;

let prefersReducedMotion = reducedMotionQuery?.matches ?? false;
const reducedMotionListeners = new Set<() => void>();

reducedMotionQuery?.addEventListener("change", (e) => {
	prefersReducedMotion = e.matches;
	for (const listener of reducedMotionListeners) listener();
});

function subscribeReducedMotion(callback: () => void) {
	reducedMotionListeners.add(callback);
	return () => {
		reducedMotionListeners.delete(callback);
	};
}

function getReducedMotionSnapshot() {
	return prefersReducedMotion;
}

const ZERO_SUBSCRIBE = (_cb: () => void) => () => {};
const ZERO_SNAPSHOT = () => 0;

/**
 * Returns the current animation time in seconds, synchronized across all consumers.
 *
 * A single shared `requestAnimationFrame` loop drives all subscribed components.
 * When `prefers-reduced-motion: reduce` is active or `enabled` is false,
 * returns `0` and no loop runs.
 */
export function useAnimationTime(enabled = true): number {
	const reducedMotion = useSyncExternalStore(
		subscribeReducedMotion,
		getReducedMotionSnapshot,
		() => false, // server snapshot
	);

	const shouldAnimate = enabled && !reducedMotion;

	return useSyncExternalStore(
		shouldAnimate ? subscribe : ZERO_SUBSCRIBE,
		shouldAnimate ? getSnapshot : ZERO_SNAPSHOT,
		getServerSnapshot,
	);
}
