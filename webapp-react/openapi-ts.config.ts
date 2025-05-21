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
});