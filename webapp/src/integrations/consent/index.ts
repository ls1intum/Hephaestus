/**
 * Cookie/tracking consent store (English-only, no external dependency).
 *
 * Essential cookies (session, CSRF, OAuth-state) are always on and not represented here — they
 * are informational only. The two optional categories are opt-in:
 *  - `analytics`        → PostHog
 *  - `errorMonitoring`  → Sentry
 *
 * The decision is persisted in `localStorage` under `CONSENT_STORAGE_KEY`. Until a
 * decision is made (`getStoredConsent` returns `null`) the banner is shown and both
 * optional integrations stay disabled. Consumers subscribe via `subscribeConsent` (used by
 * the `useSyncExternalStore`-based hooks in this module).
 *
 * "Cookie preferences" (footer + settings) re-open the banner in edit mode via
 * {@link requestConsentReopen} — pre-seeded from the current decision and cancelable, so revisiting
 * preferences never silently drops a prior choice.
 */

import { useSyncExternalStore } from "react";
import { isPosthogEnabled } from "@/integrations/posthog/config";
import { isSentryConfigured } from "@/integrations/sentry/config";

export const CONSENT_STORAGE_KEY = "hephaestus-cookie-consent";

/**
 * Which optional, consent-gated integrations are configured in THIS deployment. PostHog and Sentry
 * are the only non-essential cookie consumers; an unconfigured one can never set a cookie (its
 * provider never mounts / it never initialises).
 */
export const analyticsConfigured = isPosthogEnabled;
export const errorMonitoringConfigured = isSentryConfigured;

/**
 * Whether any optional integration is configured. When false the app uses essential cookies only —
 * which need no consent under ePrivacy Art. 5(3) / German TDDDG §25 — so the whole consent surface
 * (banner, footer link, settings section) is suppressed. A stale stored decision from when an
 * integration WAS configured is inert (nothing initialises) and is deliberately NOT cleared, so the
 * choice is honoured again if the integration ever returns.
 */
export const optionalIntegrationsAvailable = analyticsConfigured || errorMonitoringConfigured;

/**
 * Bump when the cookie categories or privacy policy change. A stored decision with a missing or
 * lower version is treated as "no decision" so existing users are re-prompted (GDPR Art. 7 — consent
 * must be informed and specific; a changed policy invalidates the prior, narrower consent).
 */
export const CONSENT_VERSION = 1;

export interface CookieConsent {
	/** PostHog analytics. */
	analytics: boolean;
	/** Sentry error monitoring. */
	errorMonitoring: boolean;
	/** ISO timestamp of when the decision was recorded. */
	decidedAt: string;
	/** Policy/category version this decision was made against (see {@link CONSENT_VERSION}). */
	version: number;
}

/** The opt-in choices a user can toggle (essential cookies are always on, not represented here). */
export type ConsentChoice = Pick<CookieConsent, "analytics" | "errorMonitoring">;

type ConsentListener = () => void;

const listeners = new Set<ConsentListener>();

function emitChange() {
	for (const listener of listeners) {
		listener();
	}
}

// Snapshot cache: useSyncExternalStore requires getSnapshot to return a referentially-stable value
// while the underlying store is unchanged — otherwise it re-renders every commit (infinite loop).
// We memoise the parsed object by the raw localStorage string and only re-parse when it changes.
let cachedRaw: string | null = null;
let cachedConsent: CookieConsent | null = null;

function parseConsent(raw: string): CookieConsent | null {
	try {
		const parsed = JSON.parse(raw) as Partial<CookieConsent>;
		if (typeof parsed?.analytics !== "boolean" || typeof parsed?.errorMonitoring !== "boolean") {
			return null;
		}
		// A decision from an older policy version is re-prompted (treated as no decision).
		if (parsed.version !== CONSENT_VERSION) {
			return null;
		}
		return {
			analytics: parsed.analytics,
			errorMonitoring: parsed.errorMonitoring,
			decidedAt: typeof parsed.decidedAt === "string" ? parsed.decidedAt : new Date().toISOString(),
			version: CONSENT_VERSION,
		};
	} catch {
		return null;
	}
}

/** Read the stored consent, or `null` when no decision has been made yet. */
export function getStoredConsent(): CookieConsent | null {
	if (typeof window === "undefined") {
		return null;
	}
	let raw: string | null;
	try {
		raw = window.localStorage.getItem(CONSENT_STORAGE_KEY);
	} catch {
		return null;
	}
	if (raw === cachedRaw) {
		return cachedConsent;
	}
	cachedRaw = raw;
	cachedConsent = raw ? parseConsent(raw) : null;
	return cachedConsent;
}

/** Persist a consent decision and notify subscribers (and other tabs via the storage event). */
export function setStoredConsent(consent: ConsentChoice) {
	if (typeof window === "undefined") {
		return;
	}
	const value: CookieConsent = {
		analytics: consent.analytics,
		errorMonitoring: consent.errorMonitoring,
		decidedAt: new Date().toISOString(),
		version: CONSENT_VERSION,
	};
	try {
		window.localStorage.setItem(CONSENT_STORAGE_KEY, JSON.stringify(value));
	} catch {
		// localStorage may be unavailable (private mode / disabled); fall back to in-memory only.
	}
	closeConsentReopen();
	emitChange();
}

// Edit-mode reopen: set when the user clicks "Cookie preferences". The banner then shows even though
// a decision exists, pre-seeded from `reopenSeed` and cancelable (so backing out keeps the prior
// choice). This is NOT a passive first-visit appearance.
let reopenRequested = false;
let reopenSeed: ConsentChoice | null = null;

/**
 * Re-open the consent banner in edit mode, pre-seeded with the current decision. Cancelling leaves
 * the existing choice untouched; saving records a new one. Satisfies GDPR Art. 7(3) (withdrawing is
 * as easy as giving — open and pick "Reject all") without destroying the prior decision on a passive
 * revisit.
 */
export function requestConsentReopen() {
	const current = getStoredConsent();
	reopenSeed = current
		? { analytics: current.analytics, errorMonitoring: current.errorMonitoring }
		: null;
	reopenRequested = true;
	emitChange();
}

/** Close the edit-mode reopen (on save or cancel). */
export function closeConsentReopen() {
	if (reopenRequested) {
		reopenRequested = false;
		emitChange();
	}
}

/** The pre-seed for a reopen, read-and-cleared so it applies once. */
export function consumeReopenSeed(): ConsentChoice | null {
	const seed = reopenSeed;
	reopenSeed = null;
	return seed;
}

/** Whether the banner is currently in explicit edit-mode reopen (non-hook accessor). */
export function isConsentReopenRequested(): boolean {
	return reopenRequested;
}

/** Subscribe to consent / reopen changes (this tab and other tabs). Returns an unsubscribe fn. */
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

/** React hook returning the current consent (or `null` until a decision is made). */
export function useCookieConsent(): CookieConsent | null {
	return useSyncExternalStore(subscribeConsent, getStoredConsent, () => null);
}

/** React hook: whether the banner was explicitly re-opened in edit mode. */
export function useConsentReopenRequested(): boolean {
	return useSyncExternalStore(subscribeConsent, isConsentReopenRequested, () => false);
}

/** Whether error-monitoring (Sentry) consent has been granted. */
export function hasErrorMonitoringConsent(): boolean {
	return getStoredConsent()?.errorMonitoring === true;
}
