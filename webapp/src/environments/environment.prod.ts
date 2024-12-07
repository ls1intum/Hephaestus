export const environment = {
  clientUrl: 'http://localhost:4200',
  serverUrl: 'http://localhost:8080',
  version: '0.0.2',
  sentry: {
    dsn: 'https://289f1f62feeb4f70a8878dc0101825cd@sentry.ase.in.tum.de/3',
    environment: 'prod'
  },
  keycloak: {
    url: 'http://localhost:8081',
    realm: 'hephaestus',
    clientId: 'hephaestus',
    skipLoginPage: false // If true, it will directly use github IDP for login
  },
  umami: {
    enabled: false,
    scriptUrl: '',
    websiteId: '',
    domains: ''
  },
  legal: {
    imprintHtml: '<p>This is the imprint.</p>',
    privacyHtml: '<p>This is the privacy policy.</p>'
  }
};
