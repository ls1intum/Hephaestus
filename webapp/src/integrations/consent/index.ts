/**
 * Cookie/tracking consent store.
 *
 * Essential cookies (session, CSRF, OAuth-state) are always on and not represented here. The two
 * optional categories are opt-in: `analytics` → PostHog, `errorMonitoring` → Sentry. Until a
 * decision is stored the banner is shown and both stay disabled.
 */
import { useSyncExternalStore } from "react";
import { z } from "zod";
import { isPosthogEnabled } from "@/integrations/posthog/config";
import { isSentryConfigured } from "@/integrations/sentry/config";

export const CONSENT_STORAGE_KEY = "hephaestus-cookie-consent";

/**
 * Configured in THIS deployment. PostHog and Sentry are the only non-essential cookie consumers, and
 * an unconfigured one can never set a cookie because it never initialises.
 */
export const analyticsConfigured = isPosthogEnabled;
export const errorMonitoringConfigured = isSentryConfigured;

/**
 * When false the app uses essential cookies only, which need no consent under ePrivacy Art. 5(3) /
 * German TDDDG §25, so the whole consent surface is suppressed. A decision stored while an
 * integration WAS configured is inert and deliberately not cleared, so it is honoured again if that
 * integration ever returns.
 */
export const optionalIntegrationsAvailable = analyticsConfigured || errorMonitoringConfigured;

/**
 * Bump when the cookie categories or privacy policy change. A stored decision with a missing or
 * lower version is treated as "no decision" so existing users are re-prompted (GDPR Art. 7 — consent
 * must be informed and specific; a changed policy invalidates the prior, narrower consent).
 */
export const CONSENT_VERSION = 1;

export interface CookieConsent {
	analytics: boolean;
	errorMonitoring: boolean;
	decidedAt: string;
	/** The {@link CONSENT_VERSION} this decision was made against. */
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

// `useSyncExternalStore` re-renders every commit unless `getSnapshot` returns a referentially stable
// value, so the parsed object is memoised by the raw string it was parsed from.
let cachedRaw: string | null = null;
let cachedConsent: CookieConsent | null = null;

/** Shape-checked rather than asserted: this value comes from localStorage, which anything can write. */
const storedConsentSchema = z.object({
	analytics: z.boolean(),
	errorMonitoring: z.boolean(),
	decidedAt: z.string().optional(),
	version: z.number(),
});

function parseConsent(raw: string): CookieConsent | null {
	let json: unknown;
	try {
		json = JSON.parse(raw);
	} catch {
		return null;
	}
	const parsed = storedConsentSchema.safeParse(json);
	if (!parsed.success || parsed.data.version !== CONSENT_VERSION) {
		return null;
	}
	return {
		analytics: parsed.data.analytics,
		errorMonitoring: parsed.data.errorMonitoring,
		decidedAt: parsed.data.decidedAt ?? new Date().toISOString(),
		version: CONSENT_VERSION,
	};
}

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

// Set while the banner is showing because the user asked for it, not because no decision exists yet.
let reopenRequested = false;

/**
 * Re-open the consent banner in edit mode. Satisfies GDPR Art. 7(3) — withdrawing consent is as easy
 * as giving it — without destroying the prior decision, which a passive revisit must not do.
 */
export function requestConsentReopen() {
	reopenRequested = true;
	emitChange();
}

export function closeConsentReopen() {
	if (reopenRequested) {
		reopenRequested = false;
		emitChange();
	}
}

export function isConsentReopenRequested(): boolean {
	return reopenRequested;
}

/** Fires for a reopen or a decision in this tab, and for a decision in any other tab. */
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

export function useCookieConsent(): CookieConsent | null {
	return useSyncExternalStore(subscribeConsent, getStoredConsent, () => null);
}

export function useConsentReopenRequested(): boolean {
	return useSyncExternalStore(subscribeConsent, isConsentReopenRequested, () => false);
}

export function hasErrorMonitoringConsent(): boolean {
	return getStoredConsent()?.errorMonitoring === true;
}
