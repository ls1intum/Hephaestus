import environment from "@/environment";
import * as Sentry from "@sentry/react";

Sentry.init({
	dsn: environment.sentry.dsn,
	environment: environment.sentry.environment,
	sendDefaultPii: true,
	integrations: [],
});
