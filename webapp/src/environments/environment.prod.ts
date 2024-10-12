export const environment = {
  clientUrl: 'http://localhost:4200',
  serverUrl: 'http://localhost:8080',
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
  }
};
