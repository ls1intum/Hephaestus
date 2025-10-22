// WEB_ENV_<VARIABLE_NAME> will be substituted with `substitute_env_variables.sh` on docker container start

const APP_VERSION_PLACEHOLDER = "WEB_ENV_APP_VERSION";
const IMAGE_TAG_PLACEHOLDER = "WEB_ENV_IMAGE_TAG";
const GIT_SHA_PLACEHOLDER = "WEB_ENV_GIT_SHA";
const BUILD_DATE_PLACEHOLDER = "WEB_ENV_BUILD_DATE";

function resolvePlaceholder(value: string): string | undefined {
        if (!value || value.startsWith("WEB_ENV_")) {
                return undefined;
        }
        return value;
}

const resolvedImageTag = resolvePlaceholder(IMAGE_TAG_PLACEHOLDER);
const resolvedGitSha =
        resolvePlaceholder(GIT_SHA_PLACEHOLDER) ?? resolvedImageTag ?? "unknown";

const resolvedVersion =
        resolvePlaceholder(APP_VERSION_PLACEHOLDER) ?? resolvedImageTag ?? resolvedGitSha;

const displayVersion =
        resolvedGitSha && resolvedVersion && resolvedVersion !== resolvedGitSha
                ? `${resolvedVersion} (${resolvedGitSha.slice(0, 7)})`
                : resolvedVersion;

export default {
        version: displayVersion,
        gitSha: resolvedGitSha,
        buildDate: resolvePlaceholder(BUILD_DATE_PLACEHOLDER),
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
