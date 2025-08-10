import * as Sentry from "@sentry/react";
import environment from "@/environment";

Sentry.init({
	dsn: environment.sentry.dsn,
	environment: environment.sentry.environment,
	sendDefaultPii: true,
	integrations: [],
});
