// Runtime environment configuration for production builds.
// Environment variables are loaded from /env-config.js which is:
// 1. NOT bundled by Vite (lives in public/ folder)
// 2. NOT cached by the browser (nginx serves with no-cache headers)
// 3. Has placeholders substituted by substitute_env_variables.sh at container start
//
// This approach ensures that when a new container version is deployed,
// browsers will always get fresh environment values without cache issues.

// Type declaration for the global __ENV__ object loaded from env-config.js
declare global {
	interface Window {
		__ENV__?: {
			APPLICATION_VERSION?: string;
			APPLICATION_CLIENT_URL?: string;
			APPLICATION_SERVER_URL?: string;
			SENTRY_ENVIRONMENT?: string;
			SENTRY_DSN?: string;
			KEYCLOAK_URL?: string;
			KEYCLOAK_REALM?: string;
			KEYCLOAK_CLIENT_ID?: string;
			POSTHOG_ENABLED?: string;
			POSTHOG_PROJECT_API_KEY?: string;
			POSTHOG_API_HOST?: string;
			LEGAL_IMPRINT_HTML?: string;
			LEGAL_PRIVACY_HTML?: string;
			TANSTACK_DEVTOOLS_ENABLED?: string;
			GIT_BRANCH?: string;
			GIT_COMMIT?: string;
			DEPLOYED_AT?: string;
		};
	}
}

// Helper to get env value, returns empty string if not set or still a placeholder
function getEnv(key: keyof NonNullable<Window["__ENV__"]>): string {
	const value = window.__ENV__?.[key] ?? "";
	// If the value still looks like a placeholder (starts with WEB_ENV_), return empty string
	if (value.startsWith("WEB_ENV_")) {
		return "";
	}
	return value;
}

const environment = {
	version: getEnv("APPLICATION_VERSION").replace(/^v/, "") || "DEV",
	buildInfo: {
		branch: getEnv("GIT_BRANCH"),
		commit: getEnv("GIT_COMMIT"),
		deployedAt: getEnv("DEPLOYED_AT"),
	},
	clientUrl: getEnv("APPLICATION_CLIENT_URL"),
	serverUrl: getEnv("APPLICATION_SERVER_URL"),
	sentry: {
		environment: getEnv("SENTRY_ENVIRONMENT"),
		dsn: getEnv("SENTRY_DSN"),
	},
	keycloak: {
		url: getEnv("KEYCLOAK_URL"),
		realm: getEnv("KEYCLOAK_REALM"),
		clientId: getEnv("KEYCLOAK_CLIENT_ID"),
	},
	posthog: {
		enabled: getEnv("POSTHOG_ENABLED"),
		projectApiKey: getEnv("POSTHOG_PROJECT_API_KEY"),
		apiHost: getEnv("POSTHOG_API_HOST"),
	},
	legal: {
		imprintHtml: getEnv("LEGAL_IMPRINT_HTML"),
		privacyHtml: getEnv("LEGAL_PRIVACY_HTML"),
	},
	devtools: {
		tanstack: getEnv("TANSTACK_DEVTOOLS_ENABLED"),
	},
};

export default environment;
