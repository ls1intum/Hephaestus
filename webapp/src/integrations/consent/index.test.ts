import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
	CONSENT_STORAGE_KEY,
	clearStoredConsent,
	consumeConsentReopen,
	getStoredConsent,
	setStoredConsent,
	subscribeConsent,
} from "./index";

describe("consent store", () => {
	beforeEach(() => {
		// getStoredConsent re-reads when the raw value changes, so clearing localStorage is enough to
		// reset the module-level snapshot cache between tests.
		localStorage.clear();
		getStoredConsent();
		consumeConsentReopen(); // drain the module-level reopen flag so it can't leak between tests
	});
	afterEach(() => localStorage.clear());

	it("returns null until a decision is recorded", () => {
		expect(getStoredConsent()).toBeNull();
	});

	it("persists a decision and reads it back", () => {
		setStoredConsent({ analytics: true, errorMonitoring: false });
		const stored = getStoredConsent();
		expect(stored?.analytics).toBe(true);
		expect(stored?.errorMonitoring).toBe(false);
		expect(typeof stored?.decidedAt).toBe("string");
	});

	it("clearStoredConsent forgets the decision (the GDPR withdrawal path)", () => {
		setStoredConsent({ analytics: true, errorMonitoring: true });
		expect(getStoredConsent()).not.toBeNull();
		clearStoredConsent();
		expect(getStoredConsent()).toBeNull();
	});

	it("treats malformed or incomplete stored values as no decision", () => {
		localStorage.setItem(CONSENT_STORAGE_KEY, "not json");
		expect(getStoredConsent()).toBeNull();
		localStorage.setItem(CONSENT_STORAGE_KEY, JSON.stringify({ analytics: true }));
		expect(getStoredConsent()).toBeNull();
	});

	it("returns a referentially stable snapshot while the raw value is unchanged", () => {
		// Guards the useSyncExternalStore getSnapshot contract: an unstable snapshot would crash the
		// consumer hook with an infinite render loop. White-box on the cache by necessity — there is no
		// cheaper way to assert the stability React requires.
		setStoredConsent({ analytics: false, errorMonitoring: true });
		expect(getStoredConsent()).toBe(getStoredConsent());
	});

	it("flags a user-initiated reopen exactly once (drives banner focus, not first-load)", () => {
		// A passive first visit must not request focus.
		expect(consumeConsentReopen()).toBe(false);
		// Withdrawing consent (the footer/settings control) requests it, once.
		clearStoredConsent();
		expect(consumeConsentReopen()).toBe(true);
		expect(consumeConsentReopen()).toBe(false);
		// Recording a decision does not request focus.
		setStoredConsent({ analytics: true, errorMonitoring: false });
		expect(consumeConsentReopen()).toBe(false);
	});

	it("notifies subscribers on set and clear", () => {
		const listener = vi.fn();
		const unsubscribe = subscribeConsent(listener);
		setStoredConsent({ analytics: true, errorMonitoring: false });
		clearStoredConsent();
		expect(listener).toHaveBeenCalledTimes(2);
		unsubscribe();
		setStoredConsent({ analytics: false, errorMonitoring: false });
		expect(listener).toHaveBeenCalledTimes(2);
	});
});
