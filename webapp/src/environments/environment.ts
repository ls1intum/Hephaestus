export const environment = {
  clientUrl: 'http://localhost:4200',
  serverUrl: 'http://localhost:8080',
  keycloak: {
    url: 'http://localhost:8081',
    realm: 'hephaestus',
    clientId: 'hephaestus',
    skipLoginPage: true // If true, it will directly use github IDP for login
  },
  umami: {
    enabled: false,
    scriptUrl: '',
    websiteId: '',
    domains: ''
  },
  legal: {
    imprintHtml: '<p>This is the imprint.</p>'
  }
};
