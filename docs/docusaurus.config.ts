import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

const config: Config = {
  title: 'Hephaestus Documentation',
  tagline: 'Process-aware mentoring for agile software teams',
  favicon: 'img/favicon.ico',

  // Future flags, see https://docusaurus.io/docs/api/docusaurus-config#future
  future: {
    v4: true,
  },

  url: 'https://ls1intum.github.io',
  baseUrl: '/Hephaestus/',
  organizationName: 'ls1intum',
  projectName: 'Hephaestus',

  onBrokenLinks: 'throw',

  // Even if you don't use internationalization, you can use this field to set
  // useful metadata like html lang. For example, if your site is Chinese, you
  // may want to replace "en" with "zh-Hans".
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  customFields: {
    productUrl: 'https://hephaestus.aet.cit.tum.de',
    repoUrl: 'https://github.com/ls1intum/Hephaestus',
  },

  presets: [
    [
      'classic',
      {
        docs: false,
        blog: {
          showReadingTime: true,
          feedOptions: {
            type: ['rss', 'atom'],
            xslt: true,
          },
          editUrl: 'https://github.com/ls1intum/Hephaestus/tree/develop/docs/blog/',
          onInlineTags: 'warn',
          onInlineAuthors: 'warn',
          onUntruncatedBlogPosts: 'warn',
        },
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  plugins: [
    [
      '@docusaurus/plugin-content-docs',
      {
        id: 'default',
        path: './user',
        routeBasePath: 'user',
        sidebarPath: './sidebars.user.ts',
        editUrl: 'https://github.com/ls1intum/Hephaestus/tree/develop/docs/user/',
        showLastUpdateAuthor: true,
        showLastUpdateTime: true,
      },
    ],
    [
      '@docusaurus/plugin-content-docs',
      {
        id: 'contributor-docs',
        path: './contributor',
        routeBasePath: 'contributor',
        sidebarPath: './sidebars.contributor.ts',
        editUrl: 'https://github.com/ls1intum/Hephaestus/tree/develop/docs/contributor/',
        showLastUpdateAuthor: true,
        showLastUpdateTime: true,
      },
    ],
    [
      '@easyops-cn/docusaurus-search-local',
      {
        hashed: true,
        language: ['en'],
        indexBlog: false,
        docsRouteBasePath: ['user', 'contributor'],
        docsDir: ['user', 'contributor'],
        searchContextByPaths: [
          {label: {en: 'User Guide'}, path: 'user'},
          {label: {en: 'Contributor Guide'}, path: 'contributor'},
        ],
        hideSearchBarWithNoSearchContext: true,
        useAllContextsWithNoSearchContext: false,
        highlightSearchTermsOnTargetPage: true,
        searchResultContextMaxLength: 60,
      },
    ],
  ],

  themeConfig: {
    image: 'img/hephaestus-social-card.jpg',
    colorMode: {
      respectPrefersColorScheme: true,
      disableSwitch: false,
    },
    metadata: [
      {name: 'keywords', content: 'Hephaestus, AI mentor, agile coaching, code review gamification, TUM'},
      {name: 'twitter:card', content: 'summary_large_image'},
      {name: 'twitter:site', content: '@ls1intum'},
      {name: 'twitter:title', content: 'Hephaestus Documentation'},
      {
        name: 'twitter:description',
        content: 'Learn how Hephaestus aligns AI mentoring with agile software development practices.',
      },
    ],
    navbar: {
      title: 'Hephaestus',
      logo: {
        alt: 'Hephaestus logo',
        src: 'img/hammer.svg',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'userSidebar',
          docsPluginId: 'default',
          position: 'left',
          label: 'User Guide',
        },
        {
          type: 'docSidebar',
          sidebarId: 'contributorSidebar',
          docsPluginId: 'contributor-docs',
          position: 'left',
          label: 'Contributor Guide',
        },
        {
          to: '/blog',
          label: 'Updates',
          position: 'left',
        },
        {
          href: 'https://hephaestus.aet.cit.tum.de',
          label: 'Open Hephaestus',
          position: 'right',
        },
        {
          href: 'https://github.com/ls1intum/Hephaestus',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Product',
          items: [
            {
              label: 'User Guide',
              to: '/user/overview',
            },
            {
              label: 'Release Notes',
              to: '/blog',
            },
            {
              label: 'Open Hephaestus',
              href: 'https://hephaestus.aet.cit.tum.de',
            },
          ],
        },
        {
          title: 'Contribute',
          items: [
            {
              label: 'Contributor Guide',
              to: '/contributor/overview',
            },
            {
              label: 'Feature Requests',
              href: 'https://github.com/ls1intum/Hephaestus/discussions/categories/feature-requests',
            },
            {
              label: 'Bug Tracker',
              href: 'https://github.com/ls1intum/Hephaestus/issues',
            },
          ],
        },
        {
          title: 'Community',
          items: [
            {
              label: 'Applied Education Technologies',
              href: 'https://aet.cit.tum.de/',
            },
            {
              label: 'GitHub Repository',
              href: 'https://github.com/ls1intum/Hephaestus',
            },
          ],
        },
      ],
      copyright: `© ${new Date().getFullYear()} Technische Universität München - Built with, Built with ❤️ by the Hephaestus Team at Applied Education Technologies (AET)`,
    },
    docs: {
      sidebar: {
        hideable: true,
        autoCollapseCategories: true,
      },
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['bash', 'json', 'yaml', 'java', 'python'],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
