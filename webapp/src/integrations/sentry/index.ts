import * as Sentry from "@sentry/react";
import environment from "@/environment";

if (environment.sentry?.dsn) {
	Sentry.init({
		dsn: environment.sentry.dsn,
		environment: environment.sentry.environment,
		// Sentry v10 made this gate IP-address inference too. We keep PII on to
		// retain v9 behavior and so error reports include the authenticated
		// account; data subjects are TUM students covered by the project DPA.
		sendDefaultPii: true,
	});
}
