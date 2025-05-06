# Environment Configuration Guide

This guide explains how environment configuration works in our React application.

## Overview

Our application uses a simple and direct environment configuration system that:

1. Uses default values for local development
2. Allows environment variable injection at container startup for production
3. Works with a single Docker image across all environments

## How It Works

### Development Mode

During development:
- The application uses settings from `src/environments/environment.ts`
- These values are suited for local development
- No additional configuration is needed

### Production Mode

In production:
1. During build, we run `copy-env-prod.js` which copies `environment.prod.ts` to `environment.ts`
2. The `environment.prod.ts` file contains placeholders like `WEB_ENV_APPLICATION_CLIENT_URL`
3. At container startup, `substitute_env_variables.sh` finds and replaces all placeholders in the bundled JS files
4. This allows us to deploy the same Docker image to different environments

## Available Environment Variables

| Variable Name | Description | Default Value |
|---------------|-------------|--------------|
| `APPLICATION_CLIENT_URL` | The public URL for the client | `http://localhost:3000` |
| `APPLICATION_SERVER_URL` | The URL for the API server | `http://localhost:8080` |
| `SENTRY_ENVIRONMENT` | Sentry environment name | `local` |
| `SENTRY_DSN` | Sentry DSN for error reporting | (From config) |
| `KEYCLOAK_URL` | Keycloak server URL | `http://localhost:8081` |
| `KEYCLOAK_REALM` | Keycloak realm name | `hephaestus` |
| `KEYCLOAK_CLIENT_ID` | Keycloak client ID | `hephaestus` |
| `KEYCLOAK_SKIP_LOGIN` | Skip login page flag | `true` |
| `POSTHOG_PROJECT_API_KEY` | PostHog API Key | (Empty) |
| `POSTHOG_API_HOST` | PostHog API host | (Empty) |
| `LEGAL_IMPRINT_HTML` | Legal imprint HTML | (Default text) |
| `LEGAL_PRIVACY_HTML` | Privacy policy HTML | (Default text) |

## Using Environment Values in Your Code

The recommended way to use environment values is to import them directly:

```tsx
// Import the environment directly
import env from '@/environments/environment';

function MyComponent() {
  // Use environment variables directly
  const serverUrl = env.serverUrl;
  
  return (
    <div>Server URL: {serverUrl}</div>
  );
}
```

## Docker Deployment

```bash
# Build the Docker image
docker build -t my-app .

# Run with environment variables
docker run -p 80:80 \
  -e APPLICATION_CLIENT_URL=https://my-app.com \
  -e APPLICATION_SERVER_URL=https://api.my-app.com \
  -e SENTRY_ENVIRONMENT=production \
  my-app
```

## Adding New Environment Variables

To add a new environment variable:

1. Add it to `src/environments/environment.ts` with a default value for development
2. Add it to `src/environments/environment.prod.ts` with a placeholder like `WEB_ENV_MY_NEW_VARIABLE`
3. Update this documentation
4. Use it in your code with `environment.myNewVariable`
