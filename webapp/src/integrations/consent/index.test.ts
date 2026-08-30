import { beforeEach, describe, expect, it } from "vitest";

import {
	CONSENT_STORAGE_KEY,
	CONSENT_VERSION,
	getStoredConsent,
	hasErrorMonitoringConsent,
	setStoredConsent,
} from "./index";

describe("cookie consent", () => {
	beforeEach(() => localStorage.clear());

	it("stores only the configured error-monitoring choice at the current version", () => {
		setStoredConsent({ errorMonitoring: true });
		expect(getStoredConsent()).toMatchObject({ errorMonitoring: true, version: CONSENT_VERSION });
		expect(hasErrorMonitoringConsent()).toBe(true);
	});

	it("rejects decisions from the former consent surface", () => {
		localStorage.setItem(
			CONSENT_STORAGE_KEY,
			JSON.stringify({ errorMonitoring: true, decidedAt: "2025-01-01T00:00:00Z", version: 1 }),
		);
		expect(getStoredConsent()).toBeNull();
	});

	it("treats malformed terminal storage as no decision", () => {
		localStorage.setItem(CONSENT_STORAGE_KEY, "not-json");
		expect(getStoredConsent()).toBeNull();
	});
});
