import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
	CONSENT_STORAGE_KEY,
	CONSENT_VERSION,
	closeConsentReopen,
	consumeReopenSeed,
	getStoredConsent,
	isConsentReopenRequested,
	requestConsentReopen,
	setStoredConsent,
	subscribeConsent,
} from "./index";

describe("consent store", () => {
	beforeEach(() => {
		// getStoredConsent re-reads when the raw value changes, so clearing localStorage is enough to
		// reset the module-level snapshot cache between tests.
		localStorage.clear();
		getStoredConsent();
		closeConsentReopen(); // drain any reopen flag so it can't leak between tests
		consumeReopenSeed();
	});
	afterEach(() => localStorage.clear());

	it("returns null until a decision is recorded", () => {
		expect(getStoredConsent()).toBeNull();
	});

	it("persists a decision (stamped with the current version) and reads it back", () => {
		setStoredConsent({ analytics: true, errorMonitoring: false });
		const stored = getStoredConsent();
		expect(stored?.analytics).toBe(true);
		expect(stored?.errorMonitoring).toBe(false);
		expect(stored?.version).toBe(CONSENT_VERSION);
		expect(typeof stored?.decidedAt).toBe("string");
	});

	it("treats malformed or incomplete stored values as no decision", () => {
		localStorage.setItem(CONSENT_STORAGE_KEY, "not json");
		expect(getStoredConsent()).toBeNull();
		localStorage.setItem(CONSENT_STORAGE_KEY, JSON.stringify({ analytics: true }));
		expect(getStoredConsent()).toBeNull();
	});

	it("re-prompts (treats as no decision) when the stored consent version is older/missing", () => {
		localStorage.setItem(
			CONSENT_STORAGE_KEY,
			JSON.stringify({ analytics: true, errorMonitoring: true, decidedAt: "x" }), // no version
		);
		expect(getStoredConsent()).toBeNull();
		localStorage.setItem(
			CONSENT_STORAGE_KEY,
			JSON.stringify({
				analytics: true,
				errorMonitoring: true,
				decidedAt: "x",
				version: CONSENT_VERSION - 1,
			}),
		);
		expect(getStoredConsent()).toBeNull();
	});

	it("returns a referentially stable snapshot while the raw value is unchanged", () => {
		// Guards the useSyncExternalStore getSnapshot contract: an unstable snapshot would crash the
		// consumer hook with an infinite render loop.
		setStoredConsent({ analytics: false, errorMonitoring: true });
		expect(getStoredConsent()).toBe(getStoredConsent());
	});

	it("requestConsentReopen opens edit mode and pre-seeds from the prior decision", () => {
		setStoredConsent({ analytics: true, errorMonitoring: false });
		expect(isConsentReopenRequested()).toBe(false);

		requestConsentReopen();
		expect(isConsentReopenRequested()).toBe(true);
		// Seed mirrors the stored choice and is consumed once.
		expect(consumeReopenSeed()).toEqual({ analytics: true, errorMonitoring: false });
		expect(consumeReopenSeed()).toBeNull();

		// Saving (or cancelling) closes edit mode.
		setStoredConsent({ analytics: false, errorMonitoring: false });
		expect(isConsentReopenRequested()).toBe(false);
	});

	it("a passive first visit does not request reopen or a seed", () => {
		expect(isConsentReopenRequested()).toBe(false);
		expect(consumeReopenSeed()).toBeNull();
	});

	it("notifies subscribers on set and on reopen", () => {
		const listener = vi.fn();
		const unsubscribe = subscribeConsent(listener);
		setStoredConsent({ analytics: true, errorMonitoring: false });
		requestConsentReopen();
		expect(listener).toHaveBeenCalledTimes(2);
		unsubscribe();
		setStoredConsent({ analytics: false, errorMonitoring: false });
		expect(listener).toHaveBeenCalledTimes(2);
	});
});
