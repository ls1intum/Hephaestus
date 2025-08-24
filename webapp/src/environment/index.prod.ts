import { version } from "@/../package.json";
// WEB_ENV_<VARIABLE_NAME> will be substituted with `substitute_env_variables.sh` on docker container start

export default {
	version,
	clientUrl: "WEB_ENV_APPLICATION_CLIENT_URL",
	serverUrl: "WEB_ENV_APPLICATION_SERVER_URL",
	sentry: {
		environment: "WEB_ENV_SENTRY_ENVIRONMENT",
		dsn: "WEB_ENV_SENTRY_DSN",
	},
	keycloak: {
		url: "WEB_ENV_KEYCLOAK_URL",
		realm: "WEB_ENV_KEYCLOAK_REALM",
		clientId: "WEB_ENV_KEYCLOAK_CLIENT_ID",
		skipLoginPage: "WEB_ENV_KEYCLOAK_SKIP_LOGIN",
	},
	posthog: {
		projectApiKey: "WEB_ENV_POSTHOG_PROJECT_API_KEY",
		apiHost: "WEB_ENV_POSTHOG_API_HOST",
	},
	legal: {
		imprintHtml: "WEB_ENV_LEGAL_IMPRINT_HTML",
		privacyHtml: "WEB_ENV_LEGAL_PRIVACY_HTML",
	},
};
