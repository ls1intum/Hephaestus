import * as Sentry from "@sentry/react";
import environment from "@/environment";
import { hasErrorMonitoringConsent } from "@/integrations/consent";

let initialized = false;

/**
 * Initialize Sentry, gated on BOTH a configured DSN and explicit error-monitoring consent.
 * Idempotent: safe to call again after the user grants consent. Without consent (or a DSN) this
 * is a no-op, so no Sentry client is created and no events are sent.
 */
export function initSentry() {
	if (initialized) {
		return;
	}
	if (!environment.sentry?.dsn || !hasErrorMonitoringConsent()) {
		return;
	}
	Sentry.init({
		dsn: environment.sentry.dsn,
		environment: environment.sentry.environment,
		// Sentry v10 made this gate IP-address inference too. We keep PII on to
		// retain v9 behavior and so error reports include the authenticated
		// account; data subjects are TUM students covered by the project DPA.
		sendDefaultPii: true,
	});
	initialized = true;
}
