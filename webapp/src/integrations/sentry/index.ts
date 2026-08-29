import * as Sentry from "@sentry/react";

import { hasErrorMonitoringConsent } from "@/integrations/consent";
import { sentryDsn, sentryEnvironment } from "@/integrations/sentry/config";

let initialized = false;

type DataCollection = NonNullable<NonNullable<Parameters<typeof Sentry.init>[0]>["dataCollection"]>;

/**
 * What an error report may carry: nothing this file did not decide to send. TUM-student data
 * subjects are the reason the rule has no exceptions.
 *
 * Supplying `dataCollection` at all switches every category you *omit* to Sentry's permissive
 * defaults, so a partial object collects more than no object would — silently, because each field
 * is optional. `Required` removes the silence: a category Sentry adds in a release fails the build
 * here rather than turning itself on. `queryParams` is excluded as the deprecated alias of
 * `urlQueryParams`, which Sentry resolves to the same value.
 *
 * None of this is load-bearing for a default browser integration today; it is stated so that adding
 * tracing, replay or an HTTP-client integration cannot quietly widen what a report carries. Search
 * params still reach Sentry inside `event.request.url` via `httpContextIntegration`, which no
 * category here gates.
 */
export const DATA_COLLECTION = {
	userInfo: false,
	cookies: false,
	httpHeaders: { request: false, response: false },
	httpBodies: [],
	urlQueryParams: false,
	graphQL: { document: false, variables: false },
	genAI: { inputs: false, outputs: false },
	databaseQueryData: false,
	stackFrameVariables: false,
	frameContextLines: 5,
} satisfies Required<Omit<DataCollection, "queryParams">>;

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
		dataCollection: DATA_COLLECTION,
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
