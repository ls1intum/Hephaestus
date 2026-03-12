import { useSyncExternalStore } from "react";

/**
 * Shared animation clock that consolidates all requestAnimationFrame loops
 * into a single global loop. Returns time in seconds (performance.now() * 0.001).
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
 * When `enabled` is false, returns `0` and no loop runs.
 */
export function useAnimationTime(enabled = true): number {
	return useSyncExternalStore(
		enabled ? subscribe : ZERO_SUBSCRIBE,
		enabled ? getSnapshot : ZERO_SNAPSHOT,
		getServerSnapshot,
	);
}
