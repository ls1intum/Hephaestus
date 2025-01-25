import { version } from '../../package.json';
// WEB_ENV_<VARIABLE_NAME> will be substituted with `subsitute_env_variables.sh` on docker container start

export const environment = {
  version,
  clientUrl: 'WEB_ENV_APPLICATION_CLIENT_URL',
  serverUrl: 'WEB_ENV_APPLICATION_SERVER_URL',
  sentry: {
    environment: 'WEB_ENV_SENTRY_ENVIRONMENT',
    dsn: 'WEB_ENV_SENTRY_DSN'
  },
  keycloak: {
    url: 'WEB_ENV_KEYCLOAK_URL',
    realm: 'WEB_ENV_KEYCLOAK_REALM',
    clientId: 'WEB_ENV_KEYCLOAK_CLIENT_ID',
    skipLoginPage: 'WEB_ENV_KEYCLOAK_SKIP_LOGIN'
  },
  umami: {
    enabled: 'WEB_ENV_UMAMI_ENABLED',
    scriptUrl: 'WEB_ENV_UMAMI_SCRIPT_URL',
    websiteId: 'WEB_ENV_UMAMI_WEBSITE_ID',
    domains: 'WEB_ENV_UMAMI_DOMAINS'
  },
  legal: {
    imprintHtml: 'WEB_ENV_LEGAL_IMPRINT_HTML',
    privacyHtml: 'WEB_ENV_LEGAL_PRIVACY_HTML'
  }
};
