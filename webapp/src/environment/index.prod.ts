// WEB_ENV_<VARIABLE_NAME> will be substituted with `substitute_env_variables.sh` on docker container start
// __APP_VERSION__ is injected at build time by Vite for proper cache invalidation

const environment = {
	// Version is injected at build time from package.json
	// This ensures JS bundle content-hashes change between versions for proper cache busting
	version: __APP_VERSION__,
	buildInfo: {
		branch: "WEB_ENV_GIT_BRANCH",
		commit: "WEB_ENV_GIT_COMMIT",
		deployedAt: "WEB_ENV_DEPLOYED_AT",
	},
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
	},
	posthog: {
		enabled: "WEB_ENV_POSTHOG_ENABLED",
		projectApiKey: "WEB_ENV_POSTHOG_PROJECT_API_KEY",
		apiHost: "WEB_ENV_POSTHOG_API_HOST",
	},
	legal: {
		imprintHtml: "WEB_ENV_LEGAL_IMPRINT_HTML",
		privacyHtml: "WEB_ENV_LEGAL_PRIVACY_HTML",
	},
	devtools: {
		tanstack: "WEB_ENV_TANSTACK_DEVTOOLS_ENABLED",
	},
};

export default environment;
