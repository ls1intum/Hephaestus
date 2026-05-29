/**
 * Cookie/tracking consent store (English-only, no external dependency).
 *
 * Essential cookies (session, CSRF, OAuth-state) are always on and not represented here — they
 * are informational only. The two optional categories are opt-in:
 *  - {@code analytics}        → PostHog
 *  - {@code errorMonitoring}  → Sentry
 *
 * The decision is persisted in {@code localStorage} under {@link CONSENT_STORAGE_KEY}. Until a
 * decision is made ({@link getStoredConsent} returns {@code null}) the banner is shown and both
 * optional integrations stay disabled. Consumers subscribe via {@link subscribeConsent} (used by
 * the {@code useState}/{@code useSyncExternalStore}-style hook in this module).
 */

import { useSyncExternalStore } from "react";

export const CONSENT_STORAGE_KEY = "hephaestus-cookie-consent";

export interface CookieConsent {
	/** PostHog analytics. */
	analytics: boolean;
	/** Sentry error monitoring. */
	errorMonitoring: boolean;
	/** ISO timestamp of when the decision was recorded. */
	decidedAt: string;
}

type ConsentListener = () => void;

const listeners = new Set<ConsentListener>();

function emitChange() {
	for (const listener of listeners) {
		listener();
	}
}

/** Read the stored consent, or {@code null} when no decision has been made yet. */
export function getStoredConsent(): CookieConsent | null {
	if (typeof window === "undefined") {
		return null;
	}
	try {
		const raw = window.localStorage.getItem(CONSENT_STORAGE_KEY);
		if (!raw) {
			return null;
		}
		const parsed = JSON.parse(raw) as Partial<CookieConsent>;
		if (typeof parsed?.analytics !== "boolean" || typeof parsed?.errorMonitoring !== "boolean") {
			return null;
		}
		return {
			analytics: parsed.analytics,
			errorMonitoring: parsed.errorMonitoring,
			decidedAt: typeof parsed.decidedAt === "string" ? parsed.decidedAt : new Date().toISOString(),
		};
	} catch {
		return null;
	}
}

/** Persist a consent decision and notify subscribers (and other tabs via the storage event). */
export function setStoredConsent(consent: Pick<CookieConsent, "analytics" | "errorMonitoring">) {
	if (typeof window === "undefined") {
		return;
	}
	const value: CookieConsent = {
		analytics: consent.analytics,
		errorMonitoring: consent.errorMonitoring,
		decidedAt: new Date().toISOString(),
	};
	try {
		window.localStorage.setItem(CONSENT_STORAGE_KEY, JSON.stringify(value));
	} catch {
		// localStorage may be unavailable (private mode / disabled); fall back to in-memory only.
	}
	emitChange();
}

/** Subscribe to consent changes (this tab and other tabs). Returns an unsubscribe fn. */
export function subscribeConsent(listener: ConsentListener): () => void {
	listeners.add(listener);
	const onStorage = (event: StorageEvent) => {
		if (event.key === CONSENT_STORAGE_KEY) {
			listener();
		}
	};
	window.addEventListener("storage", onStorage);
	return () => {
		listeners.delete(listener);
		window.removeEventListener("storage", onStorage);
	};
}

/** React hook returning the current consent (or {@code null} until a decision is made). */
export function useCookieConsent(): CookieConsent | null {
	return useSyncExternalStore(subscribeConsent, getStoredConsent, () => null);
}

/** Whether analytics (PostHog) consent has been granted. */
export function hasAnalyticsConsent(): boolean {
	return getStoredConsent()?.analytics === true;
}

/** Whether error-monitoring (Sentry) consent has been granted. */
export function hasErrorMonitoringConsent(): boolean {
	return getStoredConsent()?.errorMonitoring === true;
}
