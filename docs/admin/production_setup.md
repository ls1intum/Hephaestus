# Production Setup

TODO

## Environment Variables

- `GITHUB_WEBHOOK_SECRET`: GitHub webhook secret (`openssl rand -base64 32`)
- `GITLAB_WEBHOOK_SECRET`: GitLab webhook secret token (`openssl rand -base64 32`)
- `KEYCLOAK_GITHUB_CLIENT_ID`: GitHub identity provider client ID
- `KEYCLOAK_GITHUB_CLIENT_SECRET`: GitHub identity provider client secret
- `KEYCLOAK_ADMIN`: Keycloak admin username
- `KEYCLOAK_ADMIN_PASSWORD`: Keycloak admin password (`openssl rand -base64 32`)
- `KEYCLOAK_HEPHAESTUS_CONFIDENTIAL_CLIENT_SECRET`: Keycloak Hephaestus confidential client secret (for application server communication with Keycloak) (`openssl rand -base64 32`)
