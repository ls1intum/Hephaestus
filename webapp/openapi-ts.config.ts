import { defaultPlugins, defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
  input: '../server/application-server/openapi.yaml',
  output: 'src/api',
  plugins: [
    ...defaultPlugins,
    '@hey-api/client-fetch',
    '@tanstack/react-query',
    {
      dates: true,
      bigInt: false,
      name: '@hey-api/transformers',
    }
  ],
  // Exclude SSE endpoints - openapi-ts react-query plugin doesn't handle them correctly
  // (tries to destructure 'data' from ServerSentEventsResult which has 'stream' instead)
  // The mentor chat uses a custom transport in useMentorChat.ts
  parser: {
    filters: {
      operations: {
        exclude: ['POST /workspaces/{workspaceSlug}/mentor/chat']
      }
    }
  }
});