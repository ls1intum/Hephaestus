import * as Sentry from "@sentry/react";
import { hasErrorMonitoringConsent } from "@/integrations/consent";
import { sentryDsn, sentryEnvironment } from "@/integrations/sentry/config";

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
	if (!sentryDsn || !hasErrorMonitoringConsent()) {
		return;
	}
	Sentry.init({
		dsn: sentryDsn,
		environment: sentryEnvironment,
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
	// Detach the client synchronously so no *future* event is captured the moment consent is
	// withdrawn. `close()` also flushes buffered events and tears down the transport, but it is
	// async; we don't await it (best-effort flush) since teardown runs from a render/effect path.
	void Sentry.getClient()?.close();
	initialized = false;
}
