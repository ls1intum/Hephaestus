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
		// Don't let Sentry infer IP/PII by default — data minimisation. We already
		// attach the context an error report needs explicitly, so the safer default
		// avoids shipping inferred IP addresses for TUM-student data subjects.
		sendDefaultPii: false,
	});
	initialized = true;
}

/**
 * Tear Sentry down when error-monitoring consent is withdrawn (GDPR compliance).
 *
 * Closing the client flushes/stops the transport so no further events are captured or
 * sent; resetting the latch makes a subsequent `initSentry()` re-initialise if consent
 * is granted again. Safe to call when Sentry was never initialised (no-op).
 */
export function disableSentry() {
	if (!initialized) {
		return;
	}
	// `close()` flushes any buffered events and shuts down the transport. We don't await it:
	// teardown is fire-and-forget from the render/effect path, and a withdrawn consent must
	// stop *future* capture immediately, which detaching the client achieves synchronously.
	void Sentry.getClient()?.close();
	initialized = false;
}
