import type { StorybookConfig } from '@storybook/react-vite';

import { join, dirname } from "path"
import { Indexer, IndexerOptions } from 'storybook/internal/types';

/**
* This function is used to resolve the absolute path of a package.
* It is needed in projects that use Yarn PnP or are set up within a monorepo.
*/
function getAbsolutePath(value: string): any {
  return dirname(require.resolve(join(value, 'package.json')))
}
const config: StorybookConfig = {
  stories: [
    "../src/**/*.mdx",
    "../src/**/*.stories.@(js|jsx|mjs|ts|tsx)"
  ],
  addons: [
    getAbsolutePath('@storybook/addon-essentials'),
    getAbsolutePath('@storybook/addon-onboarding'),
    getAbsolutePath('@chromatic-com/storybook'),
    getAbsolutePath("@storybook/experimental-addon-test"),
    getAbsolutePath('@storybook/addon-themes')
  ],
  framework: {
    name: getAbsolutePath('@storybook/react-vite'),
    options: {}
  },
  // Make components top-level
  experimental_indexers: async (existingIndexers) => {
    const current = existingIndexers![0];
    const customIndexer: Indexer = {
      test: current.test,
      createIndex: async (fileName: string, options: IndexerOptions) => {
        const index = await current.createIndex(fileName, options);
        return index.map((item) => {
          return {
            ...item,
            title: item.title?.replace(/^components\//, ""), 
          }
        });
      }
    }
    return [customIndexer];
  },
};
export default config;